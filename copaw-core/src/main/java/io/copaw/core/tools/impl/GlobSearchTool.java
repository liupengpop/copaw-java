package io.copaw.core.tools.impl;

import io.copaw.core.tools.BuiltinTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Glob file search (pattern matching).
 * Maps to Python: glob_search tool in agents/tools/
 */
@Component
@Slf4j
public class GlobSearchTool implements BuiltinTool {

    private static final int MAX_RESULTS = 500;

    @Override
    public String getName() { return "glob_search"; }

    @Override
    public String getDescription() {
        return "Find files matching a glob pattern. " +
               "Parameters: pattern (string, e.g. '**/*.java'), " +
               "directory (string, optional, default current dir), " +
               "max_results (int, optional, default 500).";
    }

    @Override
    public String execute(Map<String, Object> params) throws IOException {
        String pattern = getRequired(params, "pattern");
        String dirStr = (String) params.getOrDefault("directory", ".");
        int maxResults = params.containsKey("max_results")
                ? Integer.parseInt(params.get("max_results").toString())
                : MAX_RESULTS;

        Path baseDir = Paths.get(dirStr).toAbsolutePath();
        if (!Files.exists(baseDir)) {
            return "Error: Directory not found: " + dirStr;
        }

        List<String> matches = new ArrayList<>();
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);

        Files.walkFileTree(baseDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (matcher.matches(baseDir.relativize(file))) {
                    matches.add(file.toString());
                    if (matches.size() >= maxResults) return FileVisitResult.TERMINATE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });

        if (matches.isEmpty()) return "No files found matching: " + pattern;
        StringBuilder sb = new StringBuilder("Found " + matches.size() + " file(s):\n");
        matches.forEach(m -> sb.append(m).append('\n'));
        if (matches.size() >= maxResults) sb.append("...(results truncated to ").append(maxResults).append(')');
        return sb.toString();
    }

    private String getRequired(Map<String, Object> params, String key) {
        Object val = params.get(key);
        if (val == null) throw new IllegalArgumentException("Missing required parameter: " + key);
        return val.toString();
    }
}
