package io.copaw.core.tools.impl;

import io.copaw.core.tools.BuiltinTool;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Read file contents.
 * Maps to Python: read_file tool in agents/tools/
 */
@Component
@Slf4j
public class ReadFileTool implements BuiltinTool {

    @Override
    public String getName() { return "read_file"; }

    @Override
    public String getDescription() {
        return "Read the complete contents of a file from the filesystem. " +
               "Parameters: path (string) - absolute or relative file path, " +
               "encoding (string, optional, default utf-8).";
    }

    @Override
    public String execute(Map<String, Object> params) throws IOException {
        String pathStr = requireString(params, "path");
        String encoding = (String) params.getOrDefault("encoding", "utf-8");

        Path path = Paths.get(pathStr);
        if (!Files.exists(path)) {
            return "Error: File not found: " + pathStr;
        }
        if (Files.isDirectory(path)) {
            return "Error: Path is a directory, not a file: " + pathStr;
        }

        try {
            String content = FileUtils.readFileToString(path.toFile(),
                    encoding != null ? encoding : "utf-8");
            log.debug("read_file: {} ({} chars)", pathStr, content.length());
            return content;
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    protected String requireString(Map<String, Object> params, String key) {
        Object val = params.get(key);
        if (val == null) throw new IllegalArgumentException("Missing required parameter: " + key);
        return val.toString();
    }
}
