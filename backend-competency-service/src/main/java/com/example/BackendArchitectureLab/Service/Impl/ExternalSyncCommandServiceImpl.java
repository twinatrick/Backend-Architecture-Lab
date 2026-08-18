package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IExternalSyncCommandDataAccess;
import com.example.BackendArchitectureLab.Entity.ExternalSyncCommand;
import com.example.BackendArchitectureLab.Service.ICompensationOutboxService;
import com.example.BackendArchitectureLab.Service.IExternalSyncCommandService;
import com.example.BackendArchitectureLab.Vo.CompensationOutboxDeliveryStatus;
import com.example.BackendArchitectureLab.Vo.ExternalSyncCommandPayload;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ExternalSyncCommandServiceImpl - 外部同步 durable command 寫入實作。
 * 命令與業務交易同 commit（REQUIRED 加入呼叫端交易），由 ExternalSyncWorker 可靠執行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalSyncCommandServiceImpl implements IExternalSyncCommandService {

    @Value("${external-sync.enabled:false}")
    private boolean syncEnabled;

    private final IExternalSyncCommandDataAccess commandRepository;
    private final ObjectMapper objectMapper;
    private final ICompensationOutboxService compensationOutboxService;

    @Override
    public boolean isEnabled() {
        return syncEnabled;
    }

    @Override
    @Transactional
    public void enqueue(UUID transactionId, UUID projectId, Map<UUID, Map<UUID, UUID>> memberSkillsMap,
                        Map<String, Object> beforeState) {
        if (!syncEnabled) {
            log.debug("External sync disabled by configuration, skip enqueue: projectId={}", projectId);
            return;
        }
        if (transactionId == null || projectId == null) {
            throw new IllegalArgumentException("transactionId and projectId must not be null");
        }

        ExternalSyncCommand command = new ExternalSyncCommand();
        command.setTransactionId(transactionId);
        command.setProjectId(projectId);
        command.setDeliveryStatus(CompensationOutboxDeliveryStatus.PENDING);
        command.setPayload(toJson(transactionId, memberSkillsMap, beforeState));
        commandRepository.save(command);
        log.debug("Enqueued external sync command: transactionId={}, projectId={}", transactionId, projectId);
    }

    @Override
    @Transactional
    public boolean markDeadAndEnqueueCompensation(UUID commandId, String ownerId, Long fencingVersion,
                                                  UUID transactionId, Map<String, Object> beforeState,
                                                  String errorMessage) {
        int affected = commandRepository.markDead(commandId, ownerId, fencingVersion,
                CompensationOutboxDeliveryStatus.DEAD,
                CompensationOutboxDeliveryStatus.PROCESSING, errorMessage);
        if (affected > 0) {
            compensationOutboxService.enqueueFailureAndCompensationRequired(transactionId,
                    CompensationAction.PROJECT_MEMBER_SKILLS_REBIND,
                    beforeState != null ? beforeState : new HashMap<>(), errorMessage);
            log.error("外部同步已達最大重試次數，標記 DEAD 並寫入補償請求: commandId={}, transactionId={}, ownerId={}, fencingVersion={}",
                    commandId, transactionId, ownerId, fencingVersion);
            return true;
        } else {
            log.warn("略過陳舊 worker 的 markDeadAndEnqueueCompensation（租約已被接管或已非 PROCESSING）: commandId={}, ownerId={}, fencingVersion={}",
                    commandId, ownerId, fencingVersion);
            return false;
        }
    }

    private String toJson(UUID transactionId, Map<UUID, Map<UUID, UUID>> memberSkillsMap,
                          Map<String, Object> beforeState) {
        ExternalSyncCommandPayload payload = new ExternalSyncCommandPayload();
        payload.setMemberSkillsMap(memberSkillsMap != null ? memberSkillsMap : Map.of());
        payload.setBeforeState(beforeState != null ? beforeState : new HashMap<>());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("無法序列化外部同步命令 payload: transactionId=" + transactionId, e);
        }
    }
}