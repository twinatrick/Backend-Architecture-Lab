package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IExternalSyncCommandDataAccess;
import com.example.BackendArchitectureLab.Entity.ExternalSyncCommand;
import com.example.BackendArchitectureLab.Service.IExternalSyncCommandService;
import com.example.BackendArchitectureLab.Vo.CompensationOutboxDeliveryStatus;
import com.example.BackendArchitectureLab.Vo.ExternalSyncCommandPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
public class ExternalSyncCommandServiceImpl implements IExternalSyncCommandService {

    @Value("${external-sync.enabled:false}")
    private boolean syncEnabled;

    @Autowired
    private IExternalSyncCommandDataAccess commandRepository;

    @Autowired
    private ObjectMapper objectMapper;

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