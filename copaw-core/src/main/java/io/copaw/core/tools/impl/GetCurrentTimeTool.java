package io.copaw.core.tools.impl;

import io.copaw.core.tools.BuiltinTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Get current date and time.
 * Maps to Python: get_current_time tool in agents/tools/
 */
@Component
@Slf4j
public class GetCurrentTimeTool implements BuiltinTool {

    @Override
    public String getName() { return "get_current_time"; }

    @Override
    public String getDescription() {
        return "Get the current date and time. " +
               "Parameters: timezone (string, optional, e.g. 'Asia/Shanghai', default UTC), " +
               "format (string, optional, e.g. 'yyyy-MM-dd HH:mm:ss').";
    }

    @Override
    public String execute(Map<String, Object> params) {
        String tz = (String) params.getOrDefault("timezone", "UTC");
        String format = (String) params.getOrDefault("format", "yyyy-MM-dd HH:mm:ss z");

        try {
            ZoneId zoneId = ZoneId.of(tz);
            ZonedDateTime now = ZonedDateTime.now(zoneId);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
            return now.format(formatter);
        } catch (Exception e) {
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
            return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
        }
    }
}
