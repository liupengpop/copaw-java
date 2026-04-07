package io.copaw.core.tools.impl;

import io.copaw.core.tools.BuiltinTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Execute shell commands with a configurable timeout.
 * Maps to Python: execute_shell_command tool in agents/tools/
 *
 * SECURITY: This tool should always be guarded by ToolGuardEngine.
 */
@Component
@Slf4j
public class ExecuteShellCommandTool implements BuiltinTool {

    private static final int DEFAULT_TIMEOUT_SECONDS = 120;
    private static final int MAX_OUTPUT_CHARS = 100_000;

    @Override
    public String getName() { return "execute_shell_command"; }

    @Override
    public String getDescription() {
        return "Execute a shell command and return stdout + stderr. " +
               "Parameters: command (string), working_dir (string, optional), " +
               "timeout (int, optional, seconds, default 120).";
    }

    @Override
    public String execute(Map<String, Object> params) {
        Object cmdObj = params.get("command");
        if (cmdObj == null) return "Error: Missing required parameter: command";
        String command = cmdObj.toString();

        String workingDir = (String) params.get("working_dir");
        int timeout = DEFAULT_TIMEOUT_SECONDS;
        if (params.get("timeout") instanceof Number n) {
            timeout = n.intValue();
        }

        try {
            ProcessBuilder pb = new ProcessBuilder();
            // Use shell to support pipes, redirects etc.
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                pb.command("cmd", "/c", command);
            } else {
                pb.command("/bin/bash", "-c", command);
            }
            pb.redirectErrorStream(true);
            if (workingDir != null && !workingDir.isBlank()) {
                pb.directory(Paths.get(workingDir).toFile());
            }

            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                    if (output.length() > MAX_OUTPUT_CHARS) {
                        output.append("\n[output truncated]");
                        break;
                    }
                }
            }

            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "Error: Command timed out after " + timeout + " seconds\n" + output;
            }

            int exitCode = process.exitValue();
            log.debug("execute_shell_command: exit={}, cmd={}", exitCode, command);
            String result = output.toString();
            if (exitCode != 0) {
                return "Exit code " + exitCode + ":\n" + result;
            }
            return result.isEmpty() ? "(no output)" : result;

        } catch (Exception e) {
            log.error("execute_shell_command error: {}", e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
}
