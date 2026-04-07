package io.copaw.app;

import io.copaw.common.config.CoPawRootConfig;
import io.copaw.workspace.MultiAgentManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;

/**
 * CoPaw Java - Spring Boot Application Entry Point.
 *
 * Component scan covers all io.copaw.* packages.
 */
@SpringBootApplication
@ComponentScan(basePackages = "io.copaw")
@Slf4j
public class CoPawApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(CoPawApplication.class, args);

        // Start all configured agents on application startup
        try {
            MultiAgentManager manager = ctx.getBean(MultiAgentManager.class);
            var results = manager.startAllConfiguredAgents();
            long success = results.values().stream().filter(Boolean::booleanValue).count();
            log.info("CoPaw started: {}/{} agents online", success, results.size());
        } catch (Exception e) {
            log.warn("Could not pre-start agents: {}", e.getMessage());
        }
    }
}
