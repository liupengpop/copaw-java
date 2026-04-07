package io.copaw.core.tools.impl;

import io.copaw.core.tools.BuiltinTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Edit a file by replacing exact string occurrences.
 * Maps to Python: edit_file tool in agents/tools/
 */
@Component
@Slf4j
public class EditFileTool extends ReadFileTool {

    @Override
    public String getName() { return "edit_file"; }

    @Override
    public String getDescription() {
        return "Edit a file by replacing occurrences of old_string with new_string. " +
               "Parameters: path (string), old_string (string), new_string (string), " +
               "count (int, optional, default all occurrences).";
    }

    @Override
    public String execute(Map<String, Object> params) throws IOException {
        String pathStr = requireString(params, "path");
        String oldString = requireString(params, "old_string");
        String newString = (String) params.getOrDefault("new_string", "");

        Path path = Paths.get(pathStr);
        if (!Files.exists(path)) {
            return "Error: File not found: " + pathStr;
        }

        String content = Files.readString(path);
        if (!content.contains(oldString)) {
            return "Error: old_string not found in file: " + pathStr;
        }

        Object countObj = params.get("count");
        String newContent;
        if (countObj != null) {
            int count = Integer.parseInt(countObj.toString());
            newContent = replaceFirst(content, oldString, newString, count);
        } else {
            newContent = content.replace(oldString, newString);
        }

        Files.writeString(path, newContent);
        int replacements = (content.length() - newContent.length() + newString.length())
                / Math.max(1, oldString.length() - newString.length() + 1);
        log.debug("edit_file: {} - replaced {} occurrence(s)", pathStr, replacements);
        return "Successfully edited " + pathStr;
    }

    private String replaceFirst(String str, String oldStr, String newStr, int count) {
        String result = str;
        for (int i = 0; i < count; i++) {
            int idx = result.indexOf(oldStr);
            if (idx < 0) break;
            result = result.substring(0, idx) + newStr + result.substring(idx + oldStr.length());
        }
        return result;
    }
}
