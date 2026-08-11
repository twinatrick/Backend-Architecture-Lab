package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.DataAccess.IProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectDataAccess;
import com.example.BackendArchitectureLab.Entity.UserProject;
import com.example.BackendArchitectureLab.Feign.UserServiceFeignClient;
import com.example.BackendArchitectureLab.Service.IUserProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserProjectService implements IUserProjectService {

    @Autowired
    private IUserProjectDataAccess userProjectDataAccess;
    @Autowired
    private IProjectDataAccess projectDataAccess;
    @Autowired
    private UserServiceFeignClient userServiceFeignClient;
    @Autowired
    @Lazy
    private UserProjectService self;

    @Override
    public void rebindUserProjects(UUID userId, List<UUID> projectIds) {
        if (userId == null) {
            throw new IllegalArgumentException("Key must not be null");
        }

        boolean userExists = userServiceFeignClient.existsUserById(userId);
        if (!userExists) {
            throw new IllegalArgumentException("User not found");
        }

        self.doRebindUserProjects(userId, projectIds);
    }

    /**
     * 交易內重新綁定使用者專案（由 rebindUserProjects 在交易外的 Feign 驗證後呼叫）
     */
    @Transactional
    public void doRebindUserProjects(UUID userId, List<UUID> projectIds) {
        Set<UUID> targetProjectIds = projectIds == null
                ? Set.of()
                : projectIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<UUID> existingProjectIds = userProjectDataAccess.findByUserId(userId).stream()
                .map(up -> up.getProject().getId())
                .collect(Collectors.toSet());

        for (UUID existingProjectId : existingProjectIds) {
            if (!targetProjectIds.contains(existingProjectId)) {
                userProjectDataAccess.deleteByUserIdAndProjectId(userId, existingProjectId);
            }
        }

        for (UUID targetProjectId : targetProjectIds) {
            if (!existingProjectIds.contains(targetProjectId)) {
                UserProject userProject = new UserProject();
                userProject.setUserId(userId);
                userProject.setProject(projectDataAccess.findById(targetProjectId)
                        .orElseThrow(() -> new IllegalArgumentException("Project not found")));
                userProjectDataAccess.save(userProject);
            }
        }
    }
}