package io.copaw.cron;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.function.BiConsumer;

/**
 * Manages dynamic cron jobs using Spring's TaskScheduler.
 *
 * Maps to Python: CronManager in app/crons/manager.py
 *
 * Features:
 * - Create / update / delete jobs dynamically at runtime
 * - Pause / resume jobs
 * - Run a job immediately (one-shot)
 * - Persist job specs to JSON repository
 * - Trigger agent runner on job fire
 *
 * @param jobHandler  BiConsumer<agentId, message> called when a job fires.
 *                    Wire this to AgentRunner.streamChat() in the workspace layer.
 */
@Slf4j
public class CronManager {

    private final TaskScheduler taskScheduler;
    private final JsonJobRepository repo;
    private final BiConsumer<String, String> jobHandler; // (agentId, message)

    /** jobId -> running ScheduledFuture */
    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    public CronManager(TaskScheduler taskScheduler,
                       Path workspaceDir,
                       BiConsumer<String, String> jobHandler) {
        this.taskScheduler = taskScheduler;
        this.repo = new JsonJobRepository(workspaceDir);
        this.jobHandler = jobHandler;
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /** Load persisted jobs and schedule them. */
    public void start() {
        List<CronJobSpec> jobs = repo.loadAll();
        for (CronJobSpec job : jobs) {
            if (job.isEnabled() && "ACTIVE".equals(job.getStatus())) {
                try {
                    scheduleJob(job);
                } catch (Exception e) {
                    log.warn("Failed to schedule job {} on startup: {}", job.getId(), e.getMessage());
                    job.setStatus("ERROR");
                    repo.upsert(job);
                }
            }
        }
        log.info("CronManager started with {} active job(s)", futures.size());
    }

    public void stop() {
        futures.values().forEach(f -> f.cancel(false));
        futures.clear();
        log.info("CronManager stopped");
    }

    // ------------------------------------------------------------------
    // CRUD
    // ------------------------------------------------------------------

    public CronJobSpec createOrUpdate(CronJobSpec job) {
        if (job.getId() == null || job.getId().isBlank()) {
            job.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        }

        // Cancel existing schedule if any
        cancelFuture(job.getId());

        repo.upsert(job);

        if (job.isEnabled() && "ACTIVE".equals(job.getStatus())) {
            try {
                scheduleJob(job);
                log.info("Cron job scheduled: {} ({})", job.getId(), job.getCronExpression());
            } catch (Exception e) {
                log.error("Failed to schedule job {}: {}", job.getId(), e.getMessage());
                job.setStatus("ERROR");
                repo.upsert(job);
                throw new IllegalArgumentException("Invalid cron expression: " + job.getCronExpression());
            }
        }

        return job;
    }

    public boolean delete(String jobId) {
        cancelFuture(jobId);
        return repo.delete(jobId);
    }

    public boolean pause(String jobId) {
        List<CronJobSpec> jobs = repo.loadAll();
        for (CronJobSpec job : jobs) {
            if (job.getId().equals(jobId)) {
                cancelFuture(jobId);
                job.setStatus("PAUSED");
                repo.upsert(job);
                log.info("Cron job paused: {}", jobId);
                return true;
            }
        }
        return false;
    }

    public boolean resume(String jobId) {
        List<CronJobSpec> jobs = repo.loadAll();
        for (CronJobSpec job : jobs) {
            if (job.getId().equals(jobId)) {
                job.setStatus("ACTIVE");
                repo.upsert(job);
                try {
                    scheduleJob(job);
                    log.info("Cron job resumed: {}", jobId);
                    return true;
                } catch (Exception e) {
                    log.error("Failed to resume job {}: {}", jobId, e.getMessage());
                    return false;
                }
            }
        }
        return false;
    }

    /** Trigger a job immediately, regardless of schedule. */
    public void runNow(String jobId) {
        List<CronJobSpec> jobs = repo.loadAll();
        jobs.stream()
                .filter(j -> j.getId().equals(jobId))
                .findFirst()
                .ifPresent(job -> fireJob(job));
    }

    public List<CronJobSpec> listAll() {
        return repo.loadAll();
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    private void scheduleJob(CronJobSpec job) {
        CronTrigger trigger = new CronTrigger(job.getCronExpression());
        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> fireJob(job), trigger);
        futures.put(job.getId(), future);
    }

    private void fireJob(CronJobSpec job) {
        log.info("Firing cron job: {} - agent={}", job.getId(), job.getAgentId());
        job.setLastRunAt(Instant.now());
        repo.upsert(job);

        try {
            jobHandler.accept(job.getAgentId(), job.getMessage());
        } catch (Exception e) {
            log.error("Cron job {} failed: {}", job.getId(), e.getMessage());
        }
    }

    private void cancelFuture(String jobId) {
        ScheduledFuture<?> existing = futures.remove(jobId);
        if (existing != null) existing.cancel(false);
    }
}
