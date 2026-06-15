package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.SkillLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SkillLevelRepository extends JpaRepository<SkillLevel, UUID>, JpaSpecificationExecutor<SkillLevel> {
    List<SkillLevel> findBySkillIdOrderByLevelValueAsc(UUID skillId);

    boolean existsBySkillIdAndLevelValue(UUID skillId, Integer levelValue);

    @Modifying
    @Query("delete from SkillLevel sl where sl.skill.id = :skillId")
    void deleteBySkillId(@Param("skillId") UUID skillId);
}
