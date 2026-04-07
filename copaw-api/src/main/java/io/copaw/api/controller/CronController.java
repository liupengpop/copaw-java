package io.copaw.api.controller;

import io.copaw.cron.CronJobSpec;
import io.copaw.cron.CronManager;
import io.copaw.workspace.MultiAgentManager;
import io.copaw.workspace.Workspace;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Cron job management REST API.
 * Maps to Python: app/routers/cron.py
 *
 * Endpoints:
 *   GET    /cron?agentId=              - List jobs
 *   POST   /cron?agentId=              - Create/update job
 *   DELETE /cron/{id}?agentId=         - Delete job
 *   POST   /cron/{id}/pause?agentId=   - Pause job
 *   POST   /cron/{id}/resume?agentId=  - Resume job
 *   POST   /cron/{id}/run?agentId=     - Run job now
 */
@RestController
@RequestMapping("/cron")
@RequiredArgsConstructor
@Slf4j
public class CronController {

    private final MultiAgentManager multiAgentManager;

    @GetMapping
    public Map<String, Object> listJobs(@RequestParam String agentId) {
        CronManager mgr = getCronManager(agentId);
        List<CronJobSpec> jobs = mgr.listAll();
        return Map.of("jobs", jobs, "total", jobs.size());
    }

    @PostMapping
    public Map<String, Object> createOrUpdateJob(@RequestParam String agentId,
                                                   @RequestBody CronJobSpec job) {
        job.setAgentId(agentId);
        CronManager mgr = getCronManager(agentId);
        try {
            CronJobSpec created = mgr.createOrUpdate(job);
            return Map.of("job", created);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteJob(@RequestParam String agentId,
                                          @PathVariable("id") String jobId) {
        CronManager mgr = getCronManager(agentId);
        boolean deleted = mgr.delete(jobId);
        if (!deleted) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + jobId);
        return Map.of("deleted", true, "id", jobId);
    }

    @PostMapping("/{id}/pause")
    public Map<String, Object> pauseJob(@RequestParam String agentId,
                                         @PathVariable("id") String jobId) {
        CronManager mgr = getCronManager(agentId);
        boolean ok = mgr.pause(jobId);
        if (!ok) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + jobId);
        return Map.of("paused", true, "id", jobId);
    }

    @PostMapping("/{id}/resume")
    public Map<String, Object> resumeJob(@RequestParam String agentId,
                                          @PathVariable("id") String jobId) {
        CronManager mgr = getCronManager(agentId);
        boolean ok = mgr.resume(jobId);
        if (!ok) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + jobId);
        return Map.of("resumed", true, "id", jobId);
    }

    @PostMapping("/{id}/run")
    public Map<String, Object> runNow(@RequestParam String agentId,
                                       @PathVariable("id") String jobId) {
        CronManager mgr = getCronManager(agentId);
        mgr.runNow(jobId);
        return Map.of("triggered", true, "id", jobId);
    }

    private CronManager getCronManager(String agentId) {
        Workspace ws = multiAgentManager.getOrCreate(agentId);
        CronManager cronManager = ws.getCronManager();
        if (cronManager == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CronManager not initialized for agent: " + agentId
            );
        }
        return cronManager;
    }
}
