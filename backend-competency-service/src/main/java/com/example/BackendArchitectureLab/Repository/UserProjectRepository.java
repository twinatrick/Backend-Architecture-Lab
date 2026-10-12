package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.UserProject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserProjectRepository extends JpaRepository<UserProject, UUID> {
    @Override
    @EntityGraph(attributePaths = {"project"})
    List<UserProject> findAll();

    @Override
    @EntityGraph(attributePaths = {"project"})
    Page<UserProject> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"project"})
    Optional<UserProject> findById(UUID id);

    boolean existsByUserIdAndProjectId(UUID userId, UUID projectId);
    
    boolean existsByProjectId(UUID projectId);

    @Modifying
    @Query("delete from UserProject up where up.project.id = :projectId")
    void deleteByProjectId(@Param("projectId") UUID projectId);
    
    @Modifying
    @Query("delete from UserProject up where up.userId = :userId and up.project.id = :projectId")
    void deleteByUserIdAndProjectId(@Param("userId") UUID userId, @Param("projectId") UUID projectId);
    
    @EntityGraph(attributePaths = {"project"})
    List<UserProject> findByUserId(UUID userId);

    @EntityGraph(attributePaths = {"project"})
    List<UserProject> findByProjectId(UUID projectId);
}
