package com.example.BackendArchitectureLab.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(
        name = "skill_level",
        uniqueConstraints = @UniqueConstraint(columnNames = {"skill_id", "level_value"})
)
public class SkillLevel extends BaseEntity {
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "skill_id", nullable = false)
    @JsonIgnore
    private Skill skill;

    @Column(name = "level_value", nullable = false)
    private Integer levelValue;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @JsonIgnore
    @OneToMany(mappedBy = "skillLevel", fetch = FetchType.EAGER)
    private List<UserSkill> userSkills = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "skillLevel", fetch = FetchType.EAGER)
    private List<ProjectSkill> projectSkills = new ArrayList<>();
}
