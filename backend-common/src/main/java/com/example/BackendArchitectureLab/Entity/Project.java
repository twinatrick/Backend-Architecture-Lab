package com.example.BackendArchitectureLab.Entity;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "project")
@JsonIdentityInfo(
        generator = ObjectIdGenerators.PropertyGenerator.class,
        property = "id")

public class Project extends BaseEntity {
    //skill Name
    @Column(name = "name")
    private String name;
    //專案描述
    @Column(name = "description")
    private String description;

    @JsonIgnore
    @OneToMany(mappedBy = "project",fetch = FetchType.EAGER,cascade = CascadeType.ALL)
    private List<ProjectSkill> projectSkills =new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "project",fetch = FetchType.EAGER,cascade = CascadeType.ALL)
    private List<UserProject> userProjects =new ArrayList<>();

}
