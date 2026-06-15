package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Entity.Function;
import com.example.BackendArchitectureLab.Entity.Role;
import com.example.BackendArchitectureLab.Entity.RoleFunction;
import org.springframework.data.domain.Example;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Data access interface for RoleFunction entity operations.
 * Abstracts RoleFunctionRepository operations for service layer.
 */
public interface IRoleFunctionDataAccess {

    /**
     * Find all role-function associations matching the given example.
     *
     * @param example the role-function example to match
     * @return list of matching role-function associations
     */
    List<RoleFunction> findAll(Example<RoleFunction> example);

    /**
     * Delete all given role-function associations.
     *
     * @param roleFunctions list of role-function associations to delete
     */
    void deleteAll(List<RoleFunction> roleFunctions);

    /**
     * Delete role-function association by functions and roles.
     *
     * @param functions list of function entities
     * @param roles list of role entities
     */
    void deleteByFunctionAndRole(List<Function> functions, List<Role> roles);

    /**
     * Save all role-function associations.
     *
     * @param roleFunctions list of role-function associations to save
     * @return list of saved role-function associations
     */
    List<RoleFunction> saveAll(List<RoleFunction> roleFunctions);

    /**
     * Delete role-function associations by function ID.
     *
     * @param functionId the function UUID
     */
    void deleteByFunction(UUID functionId);

    /**
     * Delete role-function associations by role ID.
     *
     * @param roleId the role UUID
     */
    void deleteByRoleKey(UUID roleId);

    /**
     * Delete all role-function associations by functions.
     *
     * @param functions collection of function entities
     */
    void deleteAllByFunctionIn(Collection<Function> functions);
}
