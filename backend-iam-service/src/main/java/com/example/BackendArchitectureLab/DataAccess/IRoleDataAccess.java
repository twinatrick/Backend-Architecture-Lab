package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Dto.Vo.Search.RoleSearchQuery;
import com.example.BackendArchitectureLab.Entity.Role;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access interface for Role entity operations.
 * Abstracts RoleRepository operations for service layer.
 */
public interface IRoleDataAccess {

    /**
     * Find roles by their keys.
     *
     * @param keys list of role UUIDs
     * @return list of roles matching the keys
     */
    List<Role> findRoleByIdIn(List<UUID> ids);

    /**
     * Check if a role exists matching the given example.
     *
     * @param example the role example to match
     * @return true if role exists, false otherwise
     */
    boolean exists(Example<Role> example);

    /**
     * Save a role entity.
     *
     * @param role the role to save
     * @return the saved role
     */
    Role save(Role role);

    /**
     * Find all roles.
     *
     * @return list of all roles
     */
    List<Role> findAll();

    /**
     * Find a role by its key.
     *
     * @param key the role UUID
     * @return optional containing the role if found
     */
    Optional<Role> findById(UUID key);

    /**
     * Delete a role.
     *
     * @param role the role to delete
     */
    void delete(Role role);

    /**
     * Find all roles by their keys.
     *
     * @param keys list of role UUIDs
     * @return list of roles matching the keys
     */
    List<Role> findAllById(List<UUID> keys);

    /**
     * Find a role by its name.
     *
     * @param name the role name
     * @return the role if found, null otherwise
     */
    Role findRoleByName(String name);
    
    /**
     * 分頁查詢角色
     *
     * @param query 查詢參數
     * @return 分頁結果
     */
    Page<Role> searchRoles(RoleSearchQuery query);
}
