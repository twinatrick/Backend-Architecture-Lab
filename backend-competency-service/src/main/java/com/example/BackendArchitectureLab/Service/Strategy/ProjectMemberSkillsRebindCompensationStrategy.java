package com.example.BackendArchitectureLab.Service.Strategy;

import com.example.BackendArchitectureLab.Exception.CompensationConflictException;
import com.example.BackendArchitectureLab.Service.ICompensationRestoreService;
import com.example.BackendArchitectureLab.Vo.BindingSnapshot;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ProjectMemberSkillsRebindCompensationStrategy - 專案成員技能重綁定的補償策略。
 * 當外部同步失敗等後續流程發生異常，導致本地事務已 commit 但整體流程失敗時，
 * 本策略會直接呼叫本地的 ICompensationRestoreService 將專案的技能綁定還原至 beforeState 記錄的歷史狀態。
 * 若事件缺少必要的 beforeState 欄位，視為永久性契約錯誤（拋出 IllegalArgumentException → 直接隔離至 DLT），
 * 避免補償被靜默跳過卻標記為成功。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectMemberSkillsRebindCompensationStrategy implements CompensationStrategy {

    private final ICompensationRestoreService compensationRestoreService;

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

        Object projectIdObj = event.getBeforeState().get("projectId");
        if (!(projectIdObj instanceof String projectIdStr)) {
            throw new IllegalArgumentException(
                    "ProjectId in beforeState must be a non-null String, but was [" + projectIdObj + "], "
                            + "cannot compensate transactionId=" + event.getTransactionId());
        }

        UUID projectId;
        try {
            projectId = UUID.fromString(projectIdStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "ProjectId in beforeState is not a valid UUID: " + projectIdStr
                            + ", cannot compensate transactionId=" + event.getTransactionId());
        }

        List<BindingSnapshot> bindings = null;
        Object bindingsObj = event.getBeforeState().get("bindings");
        if (bindingsObj != null) {
            if (!(bindingsObj instanceof List)) {
                throw new IllegalArgumentException(
                        "Bindings in beforeState must be a List when present, but was [" + bindingsObj + "], "
                                + "cannot compensate transactionId=" + event.getTransactionId());
            }
            List<?> rawBindings = (List<?>) bindingsObj;
            bindings = new ArrayList<>();
            for (Object raw : rawBindings) {
                if (!(raw instanceof Map)) {
                    throw new IllegalArgumentException(
                            "Binding entry in beforeState must be a Map, but was [" + raw + "], "
                                    + "cannot compensate transactionId=" + event.getTransactionId());
                }
                Map<?, ?> binding = (Map<?, ?>) raw;
                bindings.add(new BindingSnapshot(
                        parseBindingUuid(binding, "userId", event.getTransactionId()),
                        parseBindingUuid(binding, "skillId", event.getTransactionId()),
                        parseBindingUuid(binding, "levelId", event.getTransactionId())));
            }
        }

        Long expectedVersion = null;
        Object versionObj = event.getBeforeState().get("expectedVersion");
        if (versionObj instanceof Number) {
            expectedVersion = ((Number) versionObj).longValue();
        }

        if (expectedVersion == null) {
            throw new IllegalArgumentException(
                    "ExpectedVersion in beforeState must be present and be a Number, "
                            + "cannot compensate transactionId=" + event.getTransactionId());
        }

        log.warn("Calling ICompensationRestoreService to restore project member skills for projectId={} with eventId={}, expectedVersion={}, ownerId={}, fencingVersion={} to beforeState size={}",
                projectId, event.getEventId(), expectedVersion, ownerId, fencingVersion,
                bindings != null ? bindings.size() : 0);

        compensationRestoreService.restoreMemberSkills(
                projectId, event.getEventId(), expectedVersion, ownerId, fencingVersion, bindings);
        log.info("Successfully restored project member skills for projectId={}", projectId);
    }

    /**
     * 從 binding map 解析指定欄位的 UUID；遺失、型別錯誤或格式非法皆為永久性契約錯誤
     * （拋出 IllegalArgumentException 隔離至 DLT）。
     */
    private UUID parseBindingUuid(Map<?, ?> binding, String key, UUID transactionId) {
        Object value = binding.get(key);
        if (!(value instanceof String stringValue)) {
            throw new IllegalArgumentException(
                    "Binding field '" + key + "' in beforeState must be a non-null String, but was [" + value + "], "
                            + "cannot compensate transactionId=" + transactionId);
        }
        try {
            return UUID.fromString(stringValue);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Binding field '" + key + "' in beforeState is not a valid UUID: " + stringValue
                            + ", cannot compensate transactionId=" + transactionId);
        }
    }
}
