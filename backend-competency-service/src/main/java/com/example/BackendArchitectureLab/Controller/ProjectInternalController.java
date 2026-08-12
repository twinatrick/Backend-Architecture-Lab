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
     * @param expectedLastUpdatedTime 預期的專案最後更新時間戳，用於樂觀防護，可為空
     * @param bindings 歷史綁定 List 明細
     */
    @PostMapping("/skills/restore")
    public void restoreProjectMemberSkills(
            @RequestParam("projectId") UUID projectId,
            @RequestHeader("Idempotency-Key") String eventId,
            @RequestParam(value = "expectedLastUpdatedTime", required = false) Long expectedLastUpdatedTime,
            @RequestBody List<Map<String, String>> bindings) {
        projectUserBindingService.restoreMemberSkills(projectId, UUID.fromString(eventId), expectedLastUpdatedTime, bindings);
    }
}
