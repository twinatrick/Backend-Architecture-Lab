package com.example.BackendArchitectureLab.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "skill")
@NoArgsConstructor
public class Skill extends BaseEntity {
    //skill Name
    @Column(name = "name")
    private String name;

    //技能描述
    @Column(name = "description")
    private String description;

    @JsonIgnore
    @OneToMany(mappedBy = "skill",fetch = FetchType.EAGER,cascade = CascadeType.ALL)
    private List<SkillLevel> skillLevels =new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "skill",fetch = FetchType.EAGER,cascade = CascadeType.ALL)
    private List<UserSkill> userSkills =new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "skill",fetch = FetchType.EAGER,cascade = CascadeType.ALL)
    private List<ProjectSkill> projectSkills =new ArrayList<>();

}
