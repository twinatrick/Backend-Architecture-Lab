package com.example.BackendArchitectureLab.Service.Strategy;

import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ProjectMemberSkillsRebindCompensationStrategy - 專案成員技能重綁定的補償策略。
 * 目前該操作由資料庫交易（@Transactional）保證 rollback，因此無需額外補償動作，僅記錄。
 * <p>
 * 本策略為 log-only，無任何不可逆 side effect，可安全承受 at-least-once 語意下的重複 delivery；
 * 未來若改為呼叫外部服務（Feign/REST/DB 寫入），必須以事件 {@code eventId} 作為冪等鍵
 * （如 Idempotency-Key header）確保重複 delivery 不會產生重複業務副作用。
 */
@Slf4j
@Component
public class ProjectMemberSkillsRebindCompensationStrategy implements CompensationStrategy {

    @Override
    public boolean supports(CompensationAction action) {
        return CompensationAction.PROJECT_MEMBER_SKILLS_REBIND.equals(action);
    }

    @Override
    public void compensate(CompensationEvent event) {
        log.info("Compensating PROJECT_MEMBER_SKILLS_REBIND: transactionId={}", event.getTransactionId());
        log.info("No compensation needed for this action as @Transactional handles rollback");
    }
}