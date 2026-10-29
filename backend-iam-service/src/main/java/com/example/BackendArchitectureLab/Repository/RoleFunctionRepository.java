package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.Function;
import com.example.BackendArchitectureLab.Entity.Role;
import com.example.BackendArchitectureLab.Entity.RoleFunction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RoleFunctionRepository extends JpaRepository<RoleFunction, UUID> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RoleFunction s where s.function.id = ?1")
    void deleteByFunction(UUID key);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RoleFunction s where s.role.id = ?1")
    void deleteByRoleKey(UUID key);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RoleFunction s where s.function in ?1 and s.role in ?2")
    void deleteByFunctionAndRole(List<Function> function, List<Role> role);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RoleFunction s where s.function in ?1")
    void deleteAllByFunctionIn(Collection<Function> function);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RoleFunction s where s.function.id in :functionIds")
    void deleteByFunctionIds(@Param("functionIds") Collection<UUID> functionIds);
}
