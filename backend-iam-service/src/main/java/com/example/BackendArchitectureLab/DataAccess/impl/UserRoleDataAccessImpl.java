package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.Repository.UserRoleRepository;
import com.example.BackendArchitectureLab.DataAccess.IUserRoleDataAccess;
import com.example.BackendArchitectureLab.Entity.Role;
import com.example.BackendArchitectureLab.Entity.User;
import com.example.BackendArchitectureLab.Entity.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Implementation of IUserRoleDataAccess.
 * Delegates to Spring Data JPA UserRoleRepository.
 */
@Component
public class UserRoleDataAccessImpl implements IUserRoleDataAccess {

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Override
    public void deleteAllByUserInAndRoleIn(List<User> users, List<Role> roles) {
        userRoleRepository.deleteAllByUserInAndRoleIn(users, roles);
    }

    @Override
    public List<UserRole> saveAll(List<UserRole> userRoles) {
        return userRoleRepository.saveAll(userRoles);
    }

    @Override
    public List<UserRole> findByUserId(UUID userId) {
        return userRoleRepository.findByUserId(userId);
    }

    @Override
    public void deleteByUserId(UUID userId) {
        userRoleRepository.deleteByUserId(userId);
    }

    @Override
    public void deleteByUserIdAndRoleId(UUID userId, UUID roleId) {
        userRoleRepository.deleteByUserIdAndRoleId(userId, roleId);
    }
}
