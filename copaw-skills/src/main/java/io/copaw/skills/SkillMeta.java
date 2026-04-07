package io.copaw.skills;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Skill metadata parsed from SKILL.md frontmatter.
 * Maps to Python: skill frontmatter schema in agents/skills_manager.py
 *
 * SKILL.md format:
 * ---
 * name: My Skill
 * description: What this skill does
 * version: "1.0"
 * tools: [tool1, tool2]
 * channels: [console, telegram]
 * enabled: true
 * ---
 * (skill prompt content)
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SkillMeta {

    private String name;
    private String description = "";
    private String version = "1.0";

    /** Tools this skill provides (list of tool function names) */
    private List<String> tools = new ArrayList<>();

    /** Channels this skill is enabled on (empty = all channels) */
    private List<String> channels = new ArrayList<>();

    /** Whether this skill is enabled */
    private boolean enabled = true;

    /** SHA256 hash of the SKILL.md content (for change detection) */
    private String sha256 = "";

    /** Additional environment variables to inject when skill runs */
    private Map<String, String> env = new java.util.HashMap<>();

    /** Skill directory name (derived from folder) */
    private String dirName;

    /** Absolute path to the skill directory */
    private String skillDir;
}
