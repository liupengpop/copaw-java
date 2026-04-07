package io.copaw.skills;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses SKILL.md files and extracts metadata from frontmatter.
 * Maps to Python: skill frontmatter parsing in agents/skills_manager.py
 *
 * SKILL.md frontmatter is a YAML block between --- markers at the top of the file.
 */
@Slf4j
public class SkillMdParser {

    private static final Pattern FRONTMATTER_PATTERN =
            Pattern.compile("^---\\r?\\n(.+?)\\r?\\n---", Pattern.DOTALL);

    private static final String SKILL_MD_FILENAME = "SKILL.md";

    /**
     * Parse a SKILL.md file and return its metadata.
     *
     * @param skillDir  directory containing SKILL.md
     * @return parsed SkillMeta, or null if SKILL.md not found/invalid
     */
    public static SkillMeta parse(Path skillDir) {
        Path skillMdPath = skillDir.resolve(SKILL_MD_FILENAME);
        if (!Files.exists(skillMdPath)) {
            log.debug("No SKILL.md found in: {}", skillDir);
            return null;
        }

        try {
            String content = Files.readString(skillMdPath, StandardCharsets.UTF_8);
            SkillMeta meta = parseFrontmatter(content);
            if (meta == null) {
                log.warn("No valid frontmatter found in: {}", skillMdPath);
                return null;
            }

            meta.setDirName(skillDir.getFileName().toString());
            meta.setSkillDir(skillDir.toAbsolutePath().toString());
            meta.setSha256(sha256(content));
            return meta;

        } catch (IOException e) {
            log.error("Failed to parse SKILL.md at {}: {}", skillMdPath, e.getMessage());
            return null;
        }
    }

    /**
     * Read all skills from a skills directory.
     *
     * @param skillsDir  root directory containing skill subdirectories
     * @return list of parsed SkillMeta
     */
    public static List<SkillMeta> scanSkillsDir(Path skillsDir) {
        List<SkillMeta> skills = new ArrayList<>();
        if (!Files.exists(skillsDir) || !Files.isDirectory(skillsDir)) return skills;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(skillsDir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    SkillMeta meta = parse(entry);
                    if (meta != null) skills.add(meta);
                }
            }
        } catch (IOException e) {
            log.error("Failed to scan skills directory {}: {}", skillsDir, e.getMessage());
        }

        log.debug("Scanned {} skills from {}", skills.size(), skillsDir);
        return skills;
    }

    /**
     * Get the SKILL.md content (prompt body, excluding frontmatter).
     */
    public static String getPromptContent(Path skillDir) throws IOException {
        Path skillMdPath = skillDir.resolve(SKILL_MD_FILENAME);
        String content = Files.readString(skillMdPath, StandardCharsets.UTF_8);
        Matcher matcher = FRONTMATTER_PATTERN.matcher(content);
        if (matcher.find()) {
            // Remove frontmatter block
            return content.substring(matcher.end()).strip();
        }
        return content.strip();
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static SkillMeta parseFrontmatter(String content) {
        Matcher matcher = FRONTMATTER_PATTERN.matcher(content);
        if (!matcher.find()) return null;

        String yamlContent = matcher.group(1);
        Yaml yaml = new Yaml(new Constructor(Map.class, new LoaderOptions()));
        try {
            Map<String, Object> data = yaml.load(yamlContent);
            if (data == null) return null;

            SkillMeta meta = new SkillMeta();
            if (data.get("name") != null) meta.setName(data.get("name").toString());
            if (data.get("description") != null) meta.setDescription(data.get("description").toString());
            if (data.get("version") != null) meta.setVersion(data.get("version").toString());
            if (data.get("enabled") instanceof Boolean b) meta.setEnabled(b);

            if (data.get("tools") instanceof List<?> tools) {
                meta.setTools(tools.stream().map(Object::toString).toList());
            }
            if (data.get("channels") instanceof List<?> channels) {
                meta.setChannels(channels.stream().map(Object::toString).toList());
            }
            if (data.get("env") instanceof Map<?, ?> env) {
                Map<String, String> envMap = new HashMap<>();
                env.forEach((k, v) -> envMap.put(k.toString(), v != null ? v.toString() : ""));
                meta.setEnv(envMap);
            }

            return meta;
        } catch (Exception e) {
            log.warn("Failed to parse SKILL.md frontmatter YAML: {}", e.getMessage());
            return null;
        }
    }

    private static String sha256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }
}
