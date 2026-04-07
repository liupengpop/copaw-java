package io.copaw.core.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * File path safety guardian.
 * Blocks tool calls that attempt to access dangerous system paths.
 * Maps to Python: FilePathToolGuardian in security/tool_guard/guardians/file_guardian.py
 */
@Component
@Slf4j
public class FilePathToolGuardian implements ToolGuardian {

    @Override
    public String getName() { return "file-path-guardian"; }

    @Override
    public boolean isAlwaysRun() { return true; } // Always check file paths

    // Tools that take file path parameters
    private static final Set<String> FILE_TOOLS = Set.of(
            "read_file", "write_file", "edit_file",
            "execute_shell_command", "glob_search", "grep_search"
    );

    // Dangerous system paths that should be blocked
    private static final List<String> DANGEROUS_PATH_PREFIXES = List.of(
            "/etc/shadow", "/etc/passwd", "/etc/sudoers",
            "/private/etc/shadow", "/private/etc/passwd",
            "C:\\Windows\\System32", "C:\\Windows\\SysWOW64"
    );

    // Dangerous path patterns (regex)
    private static final List<String> DANGEROUS_PATTERNS = List.of(
            ".*\\.ssh.*",
            ".*\\.gnupg.*",
            ".*\\.aws/credentials.*",
            ".*\\.env$",
            ".*/\\.git/config$"
    );

    @Override
    public List<GuardFinding> guard(String toolName, Map<String, Object> params) {
        List<GuardFinding> findings = new ArrayList<>();

        // Extract path parameters
        List<String> pathsToCheck = extractPaths(toolName, params);

        for (String pathStr : pathsToCheck) {
            if (pathStr == null || pathStr.isBlank()) continue;

            // Check for path traversal
            if (pathStr.contains("..")) {
                findings.add(GuardFinding.builder()
                        .toolName(toolName)
                        .ruleId("file-path-traversal")
                        .severity(GuardSeverity.HIGH)
                        .description("Path traversal detected: " + pathStr)
                        .requiresApproval(true)
                        .threatCategory("path-traversal")
                        .build());
            }

            // Check dangerous path prefixes
            try {
                Path normalized = Paths.get(pathStr).normalize();
                String normalizedStr = normalized.toString();

                for (String dangerous : DANGEROUS_PATH_PREFIXES) {
                    if (normalizedStr.startsWith(dangerous)) {
                        findings.add(GuardFinding.builder()
                                .toolName(toolName)
                                .ruleId("dangerous-path-prefix")
                                .severity(GuardSeverity.CRITICAL)
                                .description("Access to dangerous system path blocked: " + pathStr)
                                .requiresApproval(false)
                                .threatCategory("system-path-access")
                                .build());
                        break;
                    }
                }

                // Check dangerous patterns
                for (String pattern : DANGEROUS_PATTERNS) {
                    if (normalizedStr.matches(pattern)) {
                        findings.add(GuardFinding.builder()
                                .toolName(toolName)
                                .ruleId("sensitive-file-pattern")
                                .severity(GuardSeverity.HIGH)
                                .description("Access to sensitive file pattern blocked: " + pathStr)
                                .requiresApproval(true)
                                .threatCategory("sensitive-file-access")
                                .build());
                        break;
                    }
                }
            } catch (InvalidPathException e) {
                log.debug("Could not parse path '{}': {}", pathStr, e.getMessage());
            }
        }

        return findings;
    }

    private List<String> extractPaths(String toolName, Map<String, Object> params) {
        List<String> paths = new ArrayList<>();
        if (!FILE_TOOLS.contains(toolName)) return paths;

        // Common path parameter names
        for (String key : List.of("path", "file_path", "filepath", "filename",
                "target_file", "source_file", "directory", "dir", "pattern")) {
            Object val = params.get(key);
            if (val instanceof String s) paths.add(s);
        }

        // Shell commands: extract file-like arguments
        if ("execute_shell_command".equals(toolName)) {
            Object cmd = params.get("command");
            if (cmd instanceof String s) {
                // Very basic extraction - just flag if it contains /etc/ etc.
                paths.add(s);
            }
        }

        return paths;
    }
}
