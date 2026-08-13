package com.example.BackendArchitectureLab.Feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CompetencyServiceFeignClient - 呼叫 competency-service 內網端點的 Feign 用戶端。
 */
@FeignClient(name = "competency-service")
public interface CompetencyServiceFeignClient {

    /**
     * 補償還原指定專案的成員技能綁定
     *
     * @param projectId 專案 ID
     * @param eventId 補償事件 ID，用於等冪去重防護
     * @param expectedVersion 快照時的專案樂觀鎖版本，用於並發守衛
     * @param bindings 歷史綁定 List 明細
     */
    @PostMapping("/project/inner/skills/restore")
    void restoreProjectMemberSkills(
            @RequestParam("projectId") UUID projectId,
            @RequestHeader("Idempotency-Key") String eventId,
            @RequestParam(value = "expectedVersion", required = false) Long expectedVersion,
            @RequestBody List<Map<String, String>> bindings);
}
