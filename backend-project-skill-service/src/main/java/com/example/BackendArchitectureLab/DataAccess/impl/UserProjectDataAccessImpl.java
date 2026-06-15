package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.Repository.UserProjectRepository;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectDataAccess;
import com.example.BackendArchitectureLab.Entity.UserProject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class UserProjectDataAccessImpl implements IUserProjectDataAccess {

    @Autowired
    private UserProjectRepository userProjectRepository;

    @Override
    public boolean existsByUserIdAndProjectId(UUID userId, UUID projectId) {
        return userProjectRepository.existsByUserIdAndProjectId(userId, projectId);
    }
    
    @Override
    public boolean existsByProjectId(UUID projectId) {
        return userProjectRepository.existsByProjectId(projectId);
    }

    @Override
    public UserProject save(UserProject userProject) {
        return userProjectRepository.save(userProject);
    }

    @Override
    public void deleteByProjectId(UUID projectId) {
        userProjectRepository.deleteByProjectId(projectId);
    }
    
    @Override
    public void deleteByUserIdAndProjectId(UUID userId, UUID projectId) {
        userProjectRepository.deleteByUserIdAndProjectId(userId, projectId);
    }
    
    @Override
    public List<UserProject> findByUserId(UUID userId) {
        return userProjectRepository.findByUserId(userId);
    }

    @Override
    public List<UserProject> findByProjectId(UUID projectId) {
        return userProjectRepository.findByProjectId(projectId);
    }
}
