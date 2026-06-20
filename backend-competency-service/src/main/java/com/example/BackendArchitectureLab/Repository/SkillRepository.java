package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SkillRepository extends JpaRepository<Skill, UUID>, JpaSpecificationExecutor<Skill> {
    List<Skill> findByName(String name);

    @Modifying
    @Query("delete from Skill s where s.id = :skillId")
    void deleteByIdHard(@Param("skillId") UUID skillId);

    @Query("SELECT s.id FROM Skill s")
    List<UUID> findAllIds();
}
