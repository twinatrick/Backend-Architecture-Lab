package com.example.BackendArchitectureLab.Service.Strategy;

import com.example.BackendArchitectureLab.Exception.CompensationConflictException;
import com.example.BackendArchitectureLab.Feign.CompetencyServiceFeignClient;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ProjectMemberSkillsRebindCompensationStrategy - 專案成員技能重綁定的補償策略。
 * 當外部同步失敗等後續流程發生異常，導致本地事務已 commit 但整體流程失敗時，
 * 本策略會透過 Feign 呼叫 competency-service 將專案的技能綁定還原至 beforeState 記錄的歷史狀態。
 * 若事件缺少必要的 beforeState 欄位，視為永久性契約錯誤（拋出 IllegalArgumentException → 直接隔離至 DLT），
 * 避免補償被靜默跳過卻標記為成功。
 */
@Slf4j
@Component
public class ProjectMemberSkillsRebindCompensationStrategy implements CompensationStrategy {

    @Autowired(required = false)
    private CompetencyServiceFeignClient competencyServiceFeignClient;

    @Override
    public boolean supports(CompensationAction action) {
        return CompensationAction.PROJECT_MEMBER_SKILLS_REBIND.equals(action);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void compensate(CompensationEvent event, String ownerId, Long fencingVersion) {
        log.info("Compensating PROJECT_MEMBER_SKILLS_REBIND: transactionId={}, ownerId={}, fencingVersion={}",
                event.getTransactionId(), ownerId, fencingVersion);

        if (event.getBeforeState() == null) {
            throw new IllegalArgumentException(
                    "BeforeState is null, cannot compensate transactionId=" + event.getTransactionId());
        }

        String projectIdStr = (String) event.getBeforeState().get("projectId");
        if (projectIdStr == null) {
            throw new IllegalArgumentException(
                    "ProjectId is missing in beforeState, cannot compensate transactionId=" + event.getTransactionId());
        }

        UUID projectId = UUID.fromString(projectIdStr);
        List<Map<String, String>> bindings = (List<Map<String, String>>) event.getBeforeState().get("bindings");

        Long expectedVersion = null;
        Object versionObj = event.getBeforeState().get("expectedVersion");
        if (versionObj instanceof Number) {
            expectedVersion = ((Number) versionObj).longValue();
        }

        log.warn("Calling competency-service to restore project member skills for projectId={} with eventId={}, expectedVersion={}, ownerId={}, fencingVersion={} to beforeState size={}",
                projectId, event.getEventId(), expectedVersion, ownerId, fencingVersion,
                bindings != null ? bindings.size() : 0);

        if (competencyServiceFeignClient != null) {
            try {
                competencyServiceFeignClient.restoreProjectMemberSkills(
                        projectId, event.getEventId().toString(), expectedVersion, ownerId, fencingVersion, bindings);
                log.info("Successfully restored project member skills for projectId={}", projectId);
            } catch (FeignException.Conflict e) {
                log.error("COMPENSATION_CONFLICT received from competency-service for projectId={}", projectId, e);
                throw new CompensationConflictException(
                        "Conflict detected in competency-service: " + e.getMessage()
                );
            }
        } else {
            log.error("CompetencyServiceFeignClient is not available, unable to compensate!");
            throw new IllegalStateException("CompetencyServiceFeignClient is null");
        }
    }
}