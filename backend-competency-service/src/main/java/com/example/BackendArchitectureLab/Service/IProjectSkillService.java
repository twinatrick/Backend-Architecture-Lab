package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.Cache.CacheListWrapper;
import com.example.BackendArchitectureLab.Vo.ProjectSkillVo;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * IProjectSkillService - 專案技能綁定操作
 */
public interface IProjectSkillService {

    /**
     * 獲取專案綁定的所有技能資訊
     *
     * @param projectId 專案 ID
     * @return 技能資訊列表
     */
    List<ProjectSkillVo> getProjectSkills(UUID projectId);

    CacheListWrapper<ProjectSkillVo> getProjectSkillsCache(UUID projectId);

    /**
     * 獲取個人專案綁定的所有技能資訊
     *
     * @param projectId 專案 ID
     * @return 技能資訊列表
     */
    List<ProjectSkillVo> getPersonalProjectSkills(UUID projectId);

    /**
     * 綁定技能到個人可操作的專案（每個技能僅能綁定一個等級）
     *
     * @param projectId 專案 ID
     * @param skillId 技能 ID
     * @param skillLevelId 技能等級 ID
     */
    void bindPersonalProjectSkill(UUID projectId, UUID skillId, UUID skillLevelId);

    /**
     * 更新個人可操作專案中的技能等級綁定
     *
     * @param projectId 專案 ID
     * @param skillId 技能 ID
     * @param skillLevelId 技能等級 ID
     */
    void updatePersonalProjectSkillLevel(UUID projectId, UUID skillId, UUID skillLevelId);

    /**
     * 解除個人可操作專案中的技能綁定
     *
     * @param projectId 專案 ID
     * @param skillId 技能 ID
     */
    void unbindPersonalProjectSkill(UUID projectId, UUID skillId);

    void rebindProjectSkills(UUID projectId, Map<UUID, UUID> skillLevelMapping);

    void rebindPersonalProjectSkills(UUID projectId, Map<UUID, UUID> skillLevelMapping);

    boolean existsProjectSkillByLevelId(UUID levelId);

    void deleteProjectSkillsBySkillId(UUID skillId);
}
