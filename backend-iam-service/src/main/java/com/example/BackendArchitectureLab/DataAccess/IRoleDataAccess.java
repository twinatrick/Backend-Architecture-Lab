package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Dto.Query.RoleSearchQuery;
import com.example.BackendArchitectureLab.Entity.Role;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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
     * Find a role by its key with role functions eagerly loaded.
     *
     * @param key the role UUID
     * @return optional containing the role with functions if found
     */
    Optional<Role> findByIdWithRoleFunctions(UUID key);

    /**
     * Find a role by its key with user roles eagerly loaded.
     *
     * @param key the role UUID
     * @return optional containing the role with user roles if found
     */
    Optional<Role> findByIdWithUserRoles(UUID key);

    /**
     * 批次查詢含 roleFunctions 與 function 的角色清單（用於解決 N+1 問題）
     *
     * @param ids 角色 UUID 清單
     * @return 含關聯資料的角色清單
     */
    List<Role> findAllByIdsWithFunctions(List<UUID> ids);

    /**
     * 分頁查詢角色
     *
     * @param query 查詢參數
     * @return 分頁結果
     */
    Page<Role> searchRoles(RoleSearchQuery query);

    /**
     * 分頁查詢所有角色
     *
     * @param pageable 分頁參數
     * @return 分頁角色實體清單
     */
    Page<Role> findAllPaged(Pageable pageable);
}
