package com.example.BackendArchitectureLab.Service.Strategy;

import com.example.BackendArchitectureLab.Feign.CompetencyServiceFeignClient;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
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
    public void compensate(CompensationEvent event) {
        log.info("Compensating PROJECT_MEMBER_SKILLS_REBIND: transactionId={}", event.getTransactionId());

        if (event.getBeforeState() == null) {
            log.warn("BeforeState is null, cannot compensate transactionId={}", event.getTransactionId());
            return;
        }

        String projectIdStr = (String) event.getBeforeState().get("projectId");
        if (projectIdStr == null) {
            log.warn("ProjectId is missing in beforeState, cannot compensate transactionId={}", event.getTransactionId());
            return;
        }

        UUID projectId = UUID.fromString(projectIdStr);
        List<Map<String, String>> bindings = (List<Map<String, String>>) event.getBeforeState().get("bindings");

        log.warn("Calling competency-service to restore project member skills for projectId={} to beforeState size={}",
                projectId, bindings != null ? bindings.size() : 0);

        if (competencyServiceFeignClient != null) {
            competencyServiceFeignClient.restoreProjectMemberSkills(projectId, bindings);
            log.info("Successfully restored project member skills for projectId={}", projectId);
        } else {
            log.error("CompetencyServiceFeignClient is not available, unable to compensate!");
            throw new IllegalStateException("CompetencyServiceFeignClient is null");
        }
    }
}