package com.example.BackendArchitectureLab.Entity;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@BatchSize(size = 50)
@Table(name = "function")
@NoArgsConstructor
@JsonIdentityInfo(
        generator = ObjectIdGenerators.PropertyGenerator.class,
        property = "id")

public class Function extends BaseEntity {
    @Column(name = "name")
    private String name="";
    @Column(name = "parent")
    private String parent="";
    @Column(name = "sort")
    private String sort="";
    @Column(name = "type")
    private Integer type;
    @JsonIgnore
    @OneToMany(mappedBy = "function", fetch = FetchType.LAZY)
    private List<RoleFunction> roleFunctions = new ArrayList<>();
}
