package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.SkillLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SkillLevelRepository extends JpaRepository<SkillLevel, UUID>, JpaSpecificationExecutor<SkillLevel> {
    @Override
    @EntityGraph(attributePaths = {"skill"})
    List<SkillLevel> findAll();

    @Override
    @EntityGraph(attributePaths = {"skill"})
    Page<SkillLevel> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"skill"})
    Page<SkillLevel> findAll(Specification<SkillLevel> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"skill"})
    Optional<SkillLevel> findById(UUID id);

    @EntityGraph(attributePaths = {"skill"})
    List<SkillLevel> findBySkillIdOrderByLevelValueAsc(UUID skillId);

    boolean existsBySkillIdAndLevelValue(UUID skillId, Integer levelValue);

    @Modifying
    @Query("delete from SkillLevel sl where sl.skill.id = :skillId")
    void deleteBySkillId(@Param("skillId") UUID skillId);
}
