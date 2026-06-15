package com.example.BackendArchitectureLab.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "user_role",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "role_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class UserRole extends BaseEntity {
    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "role_id")
    private Role role;
    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "user_id")
    private User user;
}
