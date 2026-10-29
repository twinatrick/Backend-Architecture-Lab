package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.CurrentUserSkillVo;
import com.example.BackendArchitectureLab.Dto.Request.PersonalSkillRequest;
import com.example.BackendArchitectureLab.Vo.BatchResultVo;
import com.example.BackendArchitectureLab.Dto.Query.SkillLevelSearchQuery;
import com.example.BackendArchitectureLab.Dto.Query.SkillSearchQuery;
import com.example.BackendArchitectureLab.Vo.SkillVo;
import com.example.BackendArchitectureLab.Vo.SkillLevelVo;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

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
    void rebindProjectSkills(UUID projectId, Map<UUID, UUID> skillLevelMapping);

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
     * 以 SSE 響應式分塊串流取得技能列表
     *
     * @param chunkSize 每個區塊的大小
     * @return 響應式事件流
     */
    Flux<ServerSentEvent<List<SkillVo>>> streamSkillsChunked(int chunkSize);
    
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

    SkillVo getSkillById(UUID id);

    SkillVo getSkillById(Long id);

    SkillLevelVo getSkillLevelById(UUID id);

    SkillLevelVo getSkillLevelById(Long id);

    BatchResultVo<SkillVo> getSkillsByIds(List<UUID> ids);

    BatchResultVo<SkillLevelVo> getSkillLevelsByIds(List<UUID> ids);
}
