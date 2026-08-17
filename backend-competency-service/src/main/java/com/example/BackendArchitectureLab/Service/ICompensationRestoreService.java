package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.BindingSnapshot;
import com.example.BackendArchitectureLab.Vo.CompensationRestoreResultVo;

import java.util.List;
import java.util.UUID;

/**
 * ICompensationRestoreService - 補償還原專案成員技能綁定（C-01 / PR #46 補償機制核心）。
 * 自 IProjectUserBindingService 拆分：還原面與 rebind 面職責分離（M-02 God Service 收斂）。
 */
public interface ICompensationRestoreService {

    /**
     * 補償還原專案成員技能綁定（全抹平重建強等冪模式）
     *
     * @param projectId 專案 ID
     * @param eventId 補償事件 ID，用於等冪去重
     * @param expectedVersion 快照時的專案樂觀鎖版本，用於並發守衛 (必填)
     * @param ownerId 目前認領此補償事件的處理者唯一識別碼（fencing token）
     * @param fencingVersion 目前認領的代數（單調遞增，僅最新一代持有者能執行還原）
     * @param bindings 歷史綁定 List 明細
     * @return 補償還原結果 Vo
     */
    CompensationRestoreResultVo restoreMemberSkills(UUID projectId, UUID eventId, Long expectedVersion,
                                                    String ownerId, Long fencingVersion, List<BindingSnapshot> bindings);
}
