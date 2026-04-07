package io.copaw.app.config;

import jakarta.annotation.PreDestroy;
import io.copaw.workspace.MultiAgentManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Ensures graceful shutdown: stops all workspaces before JVM exits.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GracefulShutdownHook {

    private final MultiAgentManager multiAgentManager;

    @PreDestroy
    public void onShutdown() {
        log.info("CoPaw shutting down - stopping all agents...");
        multiAgentManager.stopAll();
        log.info("CoPaw shutdown complete");
    }
}
