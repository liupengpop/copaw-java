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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Grep-style content search across files.
 * Maps to Python: grep_search tool in agents/tools/
 */
@Component
@Slf4j
public class GrepSearchTool implements BuiltinTool {

    private static final int MAX_RESULTS = 200;
    private static final int CONTEXT_LINES = 2;

    @Override
    public String getName() { return "grep_search"; }

    @Override
    public String getDescription() {
        return "Search for a regex pattern in file contents. " +
               "Parameters: pattern (string), directory (string, optional), " +
               "file_pattern (string, optional, e.g. '*.java'), " +
               "case_sensitive (bool, optional, default false), " +
               "context_lines (int, optional, default 2), " +
               "max_results (int, optional, default 200).";
    }

    @Override
    public String execute(Map<String, Object> params) throws IOException {
        String patternStr = getRequired(params, "pattern");
        String dirStr = (String) params.getOrDefault("directory", ".");
        String filePattern = (String) params.getOrDefault("file_pattern", "*");
        boolean caseSensitive = Boolean.parseBoolean(
                params.getOrDefault("case_sensitive", "false").toString());
        int contextLines = params.containsKey("context_lines")
                ? Integer.parseInt(params.get("context_lines").toString()) : CONTEXT_LINES;
        int maxResults = params.containsKey("max_results")
                ? Integer.parseInt(params.get("max_results").toString()) : MAX_RESULTS;

        Pattern regex;
        try {
            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
            regex = Pattern.compile(patternStr, flags);
        } catch (PatternSyntaxException e) {
            return "Error: Invalid regex pattern: " + e.getMessage();
        }

        Path baseDir = Paths.get(dirStr).toAbsolutePath();
        PathMatcher fileMatcher = FileSystems.getDefault().getPathMatcher("glob:" + filePattern);
        List<String> results = new ArrayList<>();
        final int[] total = {0};

        Files.walkFileTree(baseDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!fileMatcher.matches(file.getFileName())) return FileVisitResult.CONTINUE;
                if (total[0] >= maxResults) return FileVisitResult.TERMINATE;
                try {
                    List<String> lines = Files.readAllLines(file);
                    for (int i = 0; i < lines.size(); i++) {
                        if (regex.matcher(lines.get(i)).find()) {
                            results.add(formatMatch(file, lines, i, contextLines));
                            total[0]++;
                            if (total[0] >= maxResults) return FileVisitResult.TERMINATE;
                        }
                    }
                } catch (IOException e) {
                    // Skip binary/unreadable files silently
                }
                return FileVisitResult.CONTINUE;
            }
        });

        if (results.isEmpty()) return "No matches found for pattern: " + patternStr;
        StringBuilder sb = new StringBuilder("Found " + total[0] + " match(es):\n\n");
        results.forEach(r -> sb.append(r).append('\n'));
        if (total[0] >= maxResults) sb.append("\n...(results truncated)");
        return sb.toString();
    }

    private String formatMatch(Path file, List<String> lines, int lineIdx, int ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append(file).append(':').append(lineIdx + 1).append('\n');
        int start = Math.max(0, lineIdx - ctx);
        int end = Math.min(lines.size(), lineIdx + ctx + 1);
        for (int i = start; i < end; i++) {
            String prefix = (i == lineIdx) ? "> " : "  ";
            sb.append(prefix).append(i + 1).append(": ").append(lines.get(i)).append('\n');
        }
        return sb.toString();
    }

    private String getRequired(Map<String, Object> params, String key) {
        Object val = params.get(key);
        if (val == null) throw new IllegalArgumentException("Missing required parameter: " + key);
        return val.toString();
    }
}
