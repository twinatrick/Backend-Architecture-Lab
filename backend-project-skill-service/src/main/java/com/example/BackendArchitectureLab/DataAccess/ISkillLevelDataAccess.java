package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Dto.Vo.Search.SkillLevelSearchQuery;
import com.example.BackendArchitectureLab.Entity.SkillLevel;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ISkillLevelDataAccess {
    SkillLevel save(SkillLevel skillLevel);

    Optional<SkillLevel> findById(UUID id);

    List<SkillLevel> findBySkillIdOrderByLevelValueAsc(UUID skillId);

    boolean existsBySkillIdAndLevelValue(UUID skillId, Integer levelValue);

    void delete(SkillLevel skillLevel);

    void deleteBySkillId(UUID skillId);
    
    /**
     * 搜尋技能等級（支援分頁與條件查詢）
     *
     * @param query 搜尋查詢參數
     * @return 分頁結果
     */
    Page<SkillLevel> searchSkillLevels(SkillLevelSearchQuery query);
}
