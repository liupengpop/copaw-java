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
 * Write content to a file (creates directories if needed).
 * Maps to Python: write_file tool in agents/tools/
 */
@Component
@Slf4j
public class WriteFileTool extends ReadFileTool {

    @Override
    public String getName() { return "write_file"; }

    @Override
    public String getDescription() {
        return "Write content to a file. Creates parent directories if they don't exist. " +
               "Parameters: path (string), content (string), " +
               "encoding (string, optional, default utf-8).";
    }

    @Override
    public String execute(Map<String, Object> params) throws IOException {
        String pathStr = requireString(params, "path");
        String content = requireString(params, "content");
        String encoding = (String) params.getOrDefault("encoding", "utf-8");

        Path path = Paths.get(pathStr);
        Files.createDirectories(path.getParent());
        FileUtils.writeStringToFile(path.toFile(), content,
                encoding != null ? encoding : StandardCharsets.UTF_8.name());
        log.debug("write_file: {} ({} chars)", pathStr, content.length());
        return "Successfully wrote " + content.length() + " characters to " + pathStr;
    }
}
