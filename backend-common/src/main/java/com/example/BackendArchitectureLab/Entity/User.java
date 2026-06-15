package com.example.BackendArchitectureLab.Entity;

import com.example.BackendArchitectureLab.Dto.Vo.FunctionVo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Table(name = "\"user\"")
@Getter
@Setter
@Entity
@NoArgsConstructor

public class User extends BaseEntity {
    @Column(name = "name")
    private String name;
    @Column(name = "email", nullable = false, unique = true)
    private String email;
    @JsonIgnore
    @Column(name = "password")
    private String password;
    @Column(name = "phone")
    private String phone;
    @Column(name = "disabled")
    private boolean disabled =false;
    @JsonIgnore
    @OneToMany(mappedBy = "user",fetch = FetchType.EAGER,cascade = CascadeType.ALL)
    private List<UserRole> roles =new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "user",fetch = FetchType.EAGER,cascade = CascadeType.ALL)
    private List<UserSkill> userSkills =new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "user",fetch = FetchType.EAGER,cascade = CascadeType.ALL)
    private List<UserProject> userProjects =new ArrayList<>();
    @Transient
    private List<String> roleArr;

    @JsonIgnore
    @Transient
    private List<FunctionVo> permissions;

}
