package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Entity.Role;
import com.example.BackendArchitectureLab.Entity.User;
import com.example.BackendArchitectureLab.Entity.UserRole;

import java.util.List;
import java.util.UUID;

/**
 * Data access interface for UserRole entity operations.
 * Abstracts UserRoleRepository operations for service layer.
 * Note: UserRole operations are primarily handled via RoleService,
 * this interface is minimal for UserService needs.
 */
public interface IUserRoleDataAccess {

    /**
     * Delete all user-role associations for given users and roles.
     *
     * @param users list of users
     * @param roles list of roles
     */
    void deleteAllByUserInAndRoleIn(List<User> users, List<Role> roles);

    /**
     * Save all user-role associations.
     *
     * @param userRoles list of user-role associations to save
     * @return list of saved user-role associations
     */
    List<UserRole> saveAll(List<UserRole> userRoles);

    List<UserRole> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);

    void deleteByUserIdAndRoleId(UUID userId, UUID roleId);
}
