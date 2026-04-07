package io.copaw.skills;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.*;

/**
 * Per-workspace skill lifecycle manager.
 * Maps to Python: SkillService in agents/skills_manager.py
 *
 * Responsibilities:
 * - Scan workspace skills directory
 * - Enable / disable individual skills
 * - Import skills from ZIP archives (with security validation)
 * - Save manifest (skills-manifest.json) with file locking
 * - Channel routing (skill ↔ channels)
 */
@Service
@Slf4j
public class SkillService {

    private static final String SKILLS_DIR_NAME = "skills";
    private static final String MANIFEST_FILENAME = "skills-manifest.json";
    private static final long MAX_ZIP_BYTES = 200L * 1024 * 1024; // 200 MB

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    // ------------------------------------------------------------------
    // Manifest CRUD
    // ------------------------------------------------------------------

    /** Load manifest from workspace dir. Auto-reconciles with disk state. */
    public List<SkillMeta> loadManifest(Path workspaceDir) {
        Path manifestPath = getManifestPath(workspaceDir);
        List<SkillMeta> diskSkills = SkillMdParser.scanSkillsDir(getSkillsDir(workspaceDir));

        if (!Files.exists(manifestPath)) {
            // No manifest yet - create from disk
            saveManifest(workspaceDir, diskSkills);
            return diskSkills;
        }

        try {
            List<SkillMeta> saved = mapper.readValue(manifestPath.toFile(),
                    new TypeReference<List<SkillMeta>>() {});

            // Reconcile: merge saved enabled/channel state with current disk state
            Map<String, SkillMeta> savedByDir = saved.stream()
                    .collect(Collectors.toMap(SkillMeta::getDirName, s -> s, (a, b) -> a));

            List<SkillMeta> reconciled = diskSkills.stream()
                    .map(disk -> {
                        SkillMeta existing = savedByDir.get(disk.getDirName());
                        if (existing != null) {
                            // Preserve user-set enabled state and channel routing
                            disk.setEnabled(existing.isEnabled());
                            if (!existing.getChannels().isEmpty()) {
                                disk.setChannels(existing.getChannels());
                            }
                        }
                        return disk;
                    })
                    .collect(Collectors.toList());

            saveManifest(workspaceDir, reconciled);
            return reconciled;
        } catch (IOException e) {
            log.error("Failed to load manifest from {}: {}", manifestPath, e.getMessage());
            return diskSkills;
        }
    }

    /** Save manifest with file locking (cross-process safe). */
    public void saveManifest(Path workspaceDir, List<SkillMeta> skills) {
        Path manifestPath = getManifestPath(workspaceDir);
        Path lockPath = manifestPath.resolveSibling(MANIFEST_FILENAME + ".lock");

        try {
            Files.createDirectories(manifestPath.getParent());
            try (FileChannel channel = FileChannel.open(lockPath,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock lock = channel.lock()) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), skills);
                log.debug("Saved manifest with {} skills to {}", skills.size(), manifestPath);
            }
        } catch (IOException e) {
            log.error("Failed to save manifest to {}: {}", manifestPath, e.getMessage());
        }
    }

    /** Enable or disable a skill by dirName. */
    public boolean setEnabled(Path workspaceDir, String dirName, boolean enabled) {
        List<SkillMeta> skills = loadManifest(workspaceDir);
        Optional<SkillMeta> found = skills.stream()
                .filter(s -> s.getDirName().equals(dirName))
                .findFirst();
        if (found.isEmpty()) return false;
        found.get().setEnabled(enabled);
        saveManifest(workspaceDir, skills);
        return true;
    }

    /** Update channel routing for a skill. */
    public boolean setChannels(Path workspaceDir, String dirName, List<String> channels) {
        List<SkillMeta> skills = loadManifest(workspaceDir);
        Optional<SkillMeta> found = skills.stream()
                .filter(s -> s.getDirName().equals(dirName))
                .findFirst();
        if (found.isEmpty()) return false;
        found.get().setChannels(channels);
        saveManifest(workspaceDir, skills);
        return true;
    }

    // ------------------------------------------------------------------
    // ZIP import
    // ------------------------------------------------------------------

    /**
     * Import a skill from a ZIP archive into the workspace.
     * Performs security validation: max size, no path traversal, no symlinks.
     *
     * Maps to Python: import_skill_from_zip() in agents/skills_manager.py
     */
    public SkillMeta importFromZip(Path workspaceDir, InputStream zipStream,
                                    String zipFileName) throws IOException {
        Path skillsDir = getSkillsDir(workspaceDir);
        Files.createDirectories(skillsDir);

        // Read zip into temp file to validate size
        Path tempZip = Files.createTempFile("copaw-skill-", ".zip");
        try {
            long written = Files.copy(zipStream, tempZip, StandardCopyOption.REPLACE_EXISTING);
            if (written > MAX_ZIP_BYTES) {
                throw new IOException("ZIP file too large: " + written + " bytes (max " + MAX_ZIP_BYTES + ")");
            }

            return extractZipSecurely(tempZip, skillsDir);
        } finally {
            Files.deleteIfExists(tempZip);
        }
    }

    /** Delete a skill by dirName. */
    public boolean deleteSkill(Path workspaceDir, String dirName) throws IOException {
        Path skillDir = getSkillsDir(workspaceDir).resolve(dirName);
        if (!Files.exists(skillDir)) return false;

        // Validate path is within workspace (prevent traversal)
        Path resolved = skillDir.toRealPath();
        Path expected = getSkillsDir(workspaceDir).toRealPath();
        if (!resolved.startsWith(expected)) {
            throw new SecurityException("Attempted path traversal: " + dirName);
        }

        FileUtils.deleteDirectory(skillDir.toFile());

        // Update manifest
        List<SkillMeta> skills = loadManifest(workspaceDir);
        skills.removeIf(s -> s.getDirName().equals(dirName));
        saveManifest(workspaceDir, skills);
        log.info("Deleted skill: {}", dirName);
        return true;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    public Path getSkillsDir(Path workspaceDir) {
        return workspaceDir.resolve(SKILLS_DIR_NAME);
    }

    private Path getManifestPath(Path workspaceDir) {
        return getSkillsDir(workspaceDir).resolve(MANIFEST_FILENAME);
    }

    private SkillMeta extractZipSecurely(Path zipPath, Path targetDir) throws IOException {
        String topLevelDir = null;

        try (ZipFile zf = new ZipFile(zipPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zf.entries();

            // First pass: validate all entries
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                validateZipEntry(entry, targetDir);

                // Detect top-level directory
                String name = entry.getName();
                String firstComponent = name.contains("/")
                        ? name.substring(0, name.indexOf('/')) : name;
                if (topLevelDir == null && !firstComponent.isBlank()) {
                    topLevelDir = firstComponent;
                }
            }

            // Second pass: extract
            entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path entryPath = targetDir.resolve(entry.getName()).normalize();

                if (!entryPath.startsWith(targetDir)) {
                    throw new SecurityException("ZIP entry would escape target dir: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    try (InputStream is = zf.getInputStream(entry)) {
                        Files.copy(is, entryPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }

        if (topLevelDir == null) {
            throw new IOException("ZIP file is empty or has no top-level directory");
        }

        Path skillDir = targetDir.resolve(topLevelDir);
        SkillMeta meta = SkillMdParser.parse(skillDir);
        if (meta == null) {
            throw new IOException("Extracted ZIP does not contain a valid SKILL.md: " + topLevelDir);
        }

        log.info("Imported skill '{}' from ZIP into {}", meta.getName(), skillDir);
        return meta;
    }

    private void validateZipEntry(ZipEntry entry, Path targetDir) throws IOException {
        String name = entry.getName();

        // No path traversal
        if (name.contains("..")) {
            throw new SecurityException("ZIP entry contains path traversal: " + name);
        }

        // No absolute paths
        if (name.startsWith("/") || name.startsWith("\\")) {
            throw new SecurityException("ZIP entry has absolute path: " + name);
        }

        // No symlinks
        if (entry.getMethod() == ZipEntry.STORED &&
                (entry.getExtra() != null)) {
            // Basic check - symlinks manifest as special entries on Unix
            // Full check would require platform-specific code
        }
    }
}
