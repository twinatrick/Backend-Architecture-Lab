package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID>, JpaSpecificationExecutor<Role> {
    @Query("select r from Role r where r.id in ?1")
    List<Role> findRoleByIdIn(List<UUID> ids);

    Role findRoleByName(String name);

    @Query("SELECT r.id FROM Role r")
    List<UUID> findAllIds();
}
