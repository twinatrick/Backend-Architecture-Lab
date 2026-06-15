package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.UserProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserProjectRepository extends JpaRepository<UserProject, UUID> {
    boolean existsByUserIdAndProjectId(UUID userId, UUID projectId);
    
    boolean existsByProjectId(UUID projectId);

    @Modifying
    @Query("delete from UserProject up where up.project.id = :projectId")
    void deleteByProjectId(@Param("projectId") UUID projectId);
    
    @Modifying
    @Query("delete from UserProject up where up.user.id = :userId and up.project.id = :projectId")
    void deleteByUserIdAndProjectId(@Param("userId") UUID userId, @Param("projectId") UUID projectId);
    
    List<UserProject> findByUserId(UUID userId);

    List<UserProject> findByProjectId(UUID projectId);
}
