package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Dto.Vo.CurrentUserSkillVo;
import com.example.BackendArchitectureLab.Dto.Vo.PersonalSkillRequest;
import com.example.BackendArchitectureLab.Dto.Vo.Search.SkillLevelSearchQuery;
import com.example.BackendArchitectureLab.Dto.Vo.Search.SkillSearchQuery;
import com.example.BackendArchitectureLab.Dto.Vo.SkillVo;
import com.example.BackendArchitectureLab.Dto.Vo.SkillLevelVo;
import com.example.BackendArchitectureLab.Dto.Vo.Common.PageResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ISkillService {
    SkillVo addSkill(SkillVo skillVo);

    void updateSkill(SkillVo skillVo);

    List<SkillVo> getSkill();

    SkillLevelVo addSkillLevel(SkillLevelVo skillLevelVo);

    void updateSkillLevel(SkillLevelVo skillLevelVo);

    List<SkillLevelVo> getSkillLevels(String skillId);

    void deleteSkillLevel(String skillLevelId);

    void bindUserSkill(String userId, String skillId, String skillLevelId);

    void bindProjectSkill(String projectId, String skillId, String skillLevelId);

    void rebindUserSkills(UUID userId, Map<UUID, UUID> skillLevelMapping);

    void deleteSkill(SkillVo skillVo);
    
    /**
     * 搜尋所有技能（支援分頁與條件查詢）
     *
     * @param query 搜尋查詢參數
     * @return 分頁結果
     */
    PageResult<SkillVo> searchSkills(SkillSearchQuery query);
    
    /**
     * 取得當前使用者技能（合併 USER 直接綁定 + PROJECT 專案技能）
     *
     * @return 技能列表（含來源標記）
     */
    List<CurrentUserSkillVo> getCurrentUserSkills();
    
    /**
     * 搜尋當前使用者技能（支援分頁與條件查詢）
     *
     * @param query 搜尋查詢參數
     * @return 分頁結果
     */
    PageResult<CurrentUserSkillVo> searchCurrentUserSkills(SkillSearchQuery query);
    
    /**
     * 搜尋技能等級（支援分頁與條件查詢）
     *
     * @param query 搜尋查詢參數
     * @return 分頁結果
     */
    PageResult<SkillLevelVo> searchSkillLevels(SkillLevelSearchQuery query);
    
    /**
     * 新增個人技能（自動綁定當前使用者）
     *
     * @param request 個人技能請求
     * @return 新增的技能 VO
     */
    SkillVo addPersonalSkill(PersonalSkillRequest request);
    
    /**
     * 修改個人技能（僅限擁有者）
     *
     * @param skillId 技能 ID
     * @param request 個人技能請求
     */
    void updatePersonalSkill(UUID skillId, PersonalSkillRequest request);

    /**
     * 修改個人技能綁定的等級（僅修改綁定，不修改技能主資料）
     *
     * @param skillId 技能 ID
     * @param skillLevelId 技能等級 ID
     */
    void updatePersonalSkillLevel(UUID skillId, UUID skillLevelId);
    
    /**
     * 刪除個人技能（僅限擁有者）
     *
     * @param skillId 技能 ID
     */
    void deletePersonalSkill(UUID skillId);
}
