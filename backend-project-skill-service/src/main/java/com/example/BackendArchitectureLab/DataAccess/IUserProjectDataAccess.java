package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Entity.UserProject;

import java.util.List;
import java.util.UUID;

public interface IUserProjectDataAccess {
    boolean existsByUserIdAndProjectId(UUID userId, UUID projectId);
    
    boolean existsByProjectId(UUID projectId);

    UserProject save(UserProject userProject);

    void deleteByProjectId(UUID projectId);
    
    void deleteByUserIdAndProjectId(UUID userId, UUID projectId);
    
    List<UserProject> findByUserId(UUID userId);

    List<UserProject> findByProjectId(UUID projectId);
}
