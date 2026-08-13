package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.ProjectMemberSkillVo;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * IProjectUserBindingService - 專案與使用者/成員技能綁定操作
 */
public interface IProjectUserBindingService {

    /**
     * 綁定多個使用者到專案
     *
     * @param projectId 專案 ID
     * @param userIds 使用者 ID 列表
     */
    void bindUsersToProject(UUID projectId, List<String> userIds);

    /**
     * 在交易外驗證所有使用者存在（同步 Feign 呼叫不應占用資料庫交易）
     *
     * @param userIds 要驗證的使用者 ID 集合
     * @throws IllegalArgumentException 當任一使用者不存在時拋出
     */
    void validateUsersExist(Collection<UUID> userIds);

    /**
     * 清除指定使用者的專案列表快取
     *
     * @param userId 使用者 ID
     */
    void evictUserProjectsCache(UUID userId);

    /**
     * 完整覆蓋式綁定專案成員技能。
     * 所有使用者必須已是專案成員（user_project 存在），否則拋出異常。
     *
     * @param projectId 專案 ID
     * @param memberSkillsMap Map of userId -> (skillId -> levelId)
     */
    void rebindProjectMemberSkills(UUID projectId, Map<UUID, Map<UUID, UUID>> memberSkillsMap);

    /**
     * 取得專案所有成員在此專案中的技能等級綁定，供編輯專案時回填。
     *
     * @param projectId 專案 ID
     * @return 成員技能等級列表（含無技能綁定的成員，skills 為空陣列）
     */
    List<ProjectMemberSkillVo> getProjectMemberSkills(UUID projectId);

    /**
     * 補償還原專案成員技能綁定（全抹平重建強等冪模式）
     *
     * @param projectId 專案 ID
     * @param eventId 補償事件 ID，用於等冪去重
     * @param expectedVersion 快照時的專案樂觀鎖版本，用於並發守衛
     * @param bindings 歷史綁定 List 明細
     */
    void restoreMemberSkills(UUID projectId, UUID eventId, Long expectedVersion, List<Map<String, String>> bindings);
}
