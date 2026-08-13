package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Service.IProjectUserBindingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ProjectInternalController - 專案相關的內網端點，供微服務間（如 alert-service 補償機制）調用。
 */
@RestController
@RequestMapping("/project/inner")
public class ProjectInternalController {

    @Autowired
    private IProjectUserBindingService projectUserBindingService;

    /**
     * 補償還原專案成員技能綁定
     *
     * @param projectId 專案 ID
     * @param eventId 補償事件 ID，用於等冪去重
     * @param expectedVersion 快照時的專案樂觀鎖版本，用於並發守衛，可為空
     * @param bindings 歷史綁定 List 明細
     */
    @PostMapping("/skills/restore")
    public void restoreProjectMemberSkills(
            @RequestParam("projectId") UUID projectId,
            @RequestHeader("Idempotency-Key") String eventId,
            @RequestParam(value = "expectedVersion", required = false) Long expectedVersion,
            @RequestBody List<Map<String, String>> bindings) {
        projectUserBindingService.restoreMemberSkills(projectId, UUID.fromString(eventId), expectedVersion, bindings);
    }
}
