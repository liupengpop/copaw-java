package io.copaw.app.config;

import io.copaw.common.config.CoPawRootConfig;
import io.copaw.common.config.ToolGuardConfig;
import io.copaw.workspace.MultiAgentManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Application-level Spring configuration.
 */
@Configuration
public class AppConfig implements WebFluxConfigurer {

    @Value("${copaw.working-dir:${user.home}/.copaw}")
    private String workingDir;

    @Value("${copaw.config-file:config.json}")
    private String configFile;

    // ------------------------------------------------------------------
    // Core beans
    // ------------------------------------------------------------------

    @Bean
    public Path rootConfigPath() {
        return Paths.get(workingDir).resolve(configFile).toAbsolutePath();
    }

    @Bean
    public CoPawRootConfig rootConfig(Path rootConfigPath) {
        return CoPawRootConfig.load(rootConfigPath);
    }

    @Bean
    public ToolGuardConfig toolGuardConfig() {
        // Default empty config - agents override this via agent.json
        return new ToolGuardConfig();
    }

    @Bean
    public MultiAgentManager multiAgentManager(Path rootConfigPath, TaskScheduler taskScheduler) {
        return new MultiAgentManager(rootConfigPath, taskScheduler);
    }

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("copaw-cron-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        return scheduler;
    }

    // ------------------------------------------------------------------
    // WebFlux CORS configuration
    // ------------------------------------------------------------------

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
