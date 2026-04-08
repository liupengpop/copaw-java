package io.copaw.api.controller;

import io.copaw.skills.SkillMeta;
import io.copaw.skills.SkillService;
import io.copaw.workspace.MultiAgentManager;
import io.copaw.workspace.Workspace;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Skill management REST API.
 * Maps to Python: app/routers/skills.py
 *
 * Endpoints:
 *   GET    /skills?agentId=                 - List skills
 *   POST   /skills/import-zip?agentId=      - Import ZIP
 *   POST   /skills/{dir}/enable?agentId=    - Enable skill
 *   POST   /skills/{dir}/disable?agentId=   - Disable skill
 *   PUT    /skills/{dir}/channels?agentId=  - Update channel routing
 *   DELETE /skills/{dir}?agentId=           - Delete skill
 */
@RestController
@RequestMapping("/skills")
@RequiredArgsConstructor
@Slf4j
public class SkillsController {

    private final MultiAgentManager multiAgentManager;
    private final SkillService skillService;

    @GetMapping
    public Map<String, Object> listSkills(@RequestParam("agentId") String agentId) {
        Workspace ws = getWorkspace(agentId);
        List<SkillMeta> skills = skillService.loadManifest(ws.getWorkspaceDir());
        return Map.of("skills", skills, "total", skills.size());
    }

    @PostMapping("/import-zip")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> importZip(@RequestParam("agentId") String agentId,
                                         @RequestParam("file") MultipartFile file) throws IOException {
        Workspace ws = getWorkspace(agentId);
        SkillMeta meta = skillService.importFromZip(
                ws.getWorkspaceDir(),
                file.getInputStream(),
                file.getOriginalFilename());
        return Map.of("imported", true, "name", meta.getName(), "dir", meta.getDirName());
    }

    @PostMapping("/{dir}/enable")
    public Map<String, Object> enableSkill(@RequestParam("agentId") String agentId,
                                           @PathVariable("dir") String dir) {
        Workspace ws = getWorkspace(agentId);
        boolean ok = skillService.setEnabled(ws.getWorkspaceDir(), dir, true);
        if (!ok) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found: " + dir);
        return Map.of("enabled", true, "dir", dir);
    }

    @PostMapping("/{dir}/disable")
    public Map<String, Object> disableSkill(@RequestParam("agentId") String agentId,
                                            @PathVariable("dir") String dir) {
        Workspace ws = getWorkspace(agentId);
        boolean ok = skillService.setEnabled(ws.getWorkspaceDir(), dir, false);
        if (!ok) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found: " + dir);
        return Map.of("disabled", true, "dir", dir);
    }

    @PutMapping("/{dir}/channels")
    public Map<String, Object> updateChannels(@RequestParam("agentId") String agentId,
                                              @PathVariable("dir") String dir,
                                              @RequestBody ChannelsRequest req) {
        Workspace ws = getWorkspace(agentId);
        boolean ok = skillService.setChannels(ws.getWorkspaceDir(), dir, req.getChannels());
        if (!ok) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found: " + dir);
        return Map.of("updated", true, "dir", dir, "channels", req.getChannels());
    }

    @DeleteMapping("/{dir}")
    public Map<String, Object> deleteSkill(@RequestParam("agentId") String agentId,
                                           @PathVariable("dir") String dir) throws IOException {
        Workspace ws = getWorkspace(agentId);
        boolean ok = skillService.deleteSkill(ws.getWorkspaceDir(), dir);
        if (!ok) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found: " + dir);
        return Map.of("deleted", true, "dir", dir);
    }

    private Workspace getWorkspace(String agentId) {
        return multiAgentManager.getOrCreate(agentId);
    }

    @Data
    public static class ChannelsRequest {
        private List<String> channels;
    }
}
