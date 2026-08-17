package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Service.ICompensationRestoreService;
import com.example.BackendArchitectureLab.Vo.BindingSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * ProjectInternalController - 專案相關的內網端點，供微服務間（如 alert-service 補償機制）調用。
 */
@RestController
@RequestMapping("/project/inner")
public class ProjectInternalController {

    @Autowired
    private ICompensationRestoreService compensationRestoreService;

    /**
     * 補償還原專案成員技能綁定
     *
     * @param projectId 專案 ID
     * @param eventId 補償事件 ID，用於等冪去重
     * @param expectedVersion 快照時的專案樂觀鎖版本，用於並發守衛，可為空
     * @param ownerId 目前認領此補償事件的處理者唯一識別碼（fencing token）
     * @param fencingVersion 目前認領的代數（單調遞增，僅最新一代持有者能執行還原）
     * @param bindings 歷史綁定 List 明細
     */
    @PostMapping("/skills/restore")
    public void restoreProjectMemberSkills(
            @RequestParam("projectId") UUID projectId,
            @RequestHeader("Idempotency-Key") String eventId,
            @RequestParam(value = "expectedVersion", required = false) Long expectedVersion,
            @RequestHeader("X-Fencing-Owner") String ownerId,
            @RequestHeader("X-Fencing-Version") Long fencingVersion,
            @RequestBody List<BindingSnapshot> bindings) {
        compensationRestoreService.restoreMemberSkills(
                projectId, UUID.fromString(eventId), expectedVersion, ownerId, fencingVersion, bindings);
    }
}
