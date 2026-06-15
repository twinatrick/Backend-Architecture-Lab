package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Dto.Vo.Search.SkillSearchQuery;
import com.example.BackendArchitectureLab.Entity.Skill;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access interface for Skill entity operations.
 * Abstracts SkillRepository operations for service layer.
 */
public interface ISkillDataAccess {

    /**
     * Save a skill entity.
     *
     * @param skill the skill to save
     * @return the saved skill
     */
    Skill save(Skill skill);

    /**
     * Check if a skill exists matching the given example.
     *
     * @param example the skill example to match
     * @return true if a matching skill exists
     */
    boolean exists(Example<Skill> example);

    /**
     * Find all skills.
     *
     * @return list of all skills
     */
    List<Skill> findAll();

    /**
     * Find a skill by its ID.
     *
     * @param key the skill UUID
     * @return optional containing the skill if found
     */
    Optional<Skill> findById(UUID key);

    /**
     * Delete a skill.
     *
     * @param skill the skill to delete
     */
    void delete(Skill skill);

    /**
     * Delete a skill by id with bulk operation.
     *
     * @param skillId the skill id to delete
     */
    void deleteById(UUID skillId);
    
    /**
     * 搜尋技能（支援分頁與條件查詢）
     *
     * @param query 搜尋查詢參數
     * @return 分頁結果
     */
    Page<Skill> searchSkills(SkillSearchQuery query);
}
