package io.copaw.cron;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.copaw.common.json.CoPawObjectMapperFactory;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON-file based job repository.
 * Maps to Python: JsonJobRepository in app/crons/repo/json_repo.py
 *
 * Stores cron job specs in <workspace_dir>/crons/jobs.json
 */
@Slf4j
public class JsonJobRepository {

    private static final String CRONS_DIR = "crons";
    private static final String JOBS_FILE = "jobs.json";

    private final Path jobsFilePath;
    private final ObjectMapper mapper;

    public JsonJobRepository(Path workspaceDir) {
        this.jobsFilePath = workspaceDir.resolve(CRONS_DIR).resolve(JOBS_FILE);
        this.mapper = CoPawObjectMapperFactory.create();
    }

    public List<CronJobSpec> loadAll() {
        if (!Files.exists(jobsFilePath)) return new ArrayList<>();
        try {
            return mapper.readValue(jobsFilePath.toFile(),
                    new TypeReference<List<CronJobSpec>>() {});
        } catch (IOException e) {
            log.error("Failed to load cron jobs from {}: {}", jobsFilePath, e.getMessage());
            return new ArrayList<>();
        }
    }

    public void saveAll(List<CronJobSpec> jobs) {
        try {
            Files.createDirectories(jobsFilePath.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(jobsFilePath.toFile(), jobs);
        } catch (IOException e) {
            log.error("Failed to save cron jobs to {}: {}", jobsFilePath, e.getMessage());
        }
    }

    public void upsert(CronJobSpec job) {
        List<CronJobSpec> jobs = loadAll();
        jobs.removeIf(j -> j.getId().equals(job.getId()));
        jobs.add(job);
        saveAll(jobs);
    }

    public boolean delete(String jobId) {
        List<CronJobSpec> jobs = loadAll();
        boolean removed = jobs.removeIf(j -> j.getId().equals(jobId));
        if (removed) saveAll(jobs);
        return removed;
    }
}
