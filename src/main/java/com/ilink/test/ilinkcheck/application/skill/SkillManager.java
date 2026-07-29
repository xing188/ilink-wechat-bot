package com.ilink.test.ilinkcheck.application.skill;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class SkillManager {

    private final Map<String, Skill> skillsById;

    public SkillManager(List<Skill> skills) {
        this.skillsById = skills.stream()
                .collect(Collectors.toMap(Skill::id, skill -> skill, (a, b) -> a));
    }

    public Optional<Skill> findById(String id) {
        return Optional.ofNullable(skillsById.get(id));
    }

    public List<Skill> allSkills() {
        return List.copyOf(skillsById.values());
    }
}
