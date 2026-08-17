package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.ICompensationRestoreLogDataAccess;
import com.example.BackendArchitectureLab.Entity.CompensationRestoreLog;
import com.example.BackendArchitectureLab.Service.ICompensationRestoreClaimService;
import com.example.BackendArchitectureLab.Vo.BindingSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CompensationRestoreClaimService - 補償還原認領與 fencing token 管理實作（M-02 拆分）。
 * 由 CompensationRestoreService 委派，封裝認領的持久化權威來源與資料庫級並發守衛。
 */
@Slf4j
@Service
public class CompensationRestoreClaimService implements ICompensationRestoreClaimService {

    @Autowired
    private ICompensationRestoreLogDataAccess restoreLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${compensation.restore.lease-seconds:300}")
    private long restoreLeaseSeconds;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claimRestoreEvent(UUID eventId, UUID projectId, String ownerId, Long fencingVersion,
                                     List<BindingSnapshot> bindings) {
        try {
            Optional<CompensationRestoreLog> existing = restoreLogRepository.findById(eventId);
            if (existing.isPresent() && "SUCCESS".equals(existing.get().getStatus())) {
                return false;
            }

            if (existing.isEmpty()) {
                CompensationRestoreLog claim = new CompensationRestoreLog();
                claim.setEventId(eventId);
                claim.setProjectId(projectId);
                claim.setProcessedAt(new Date());
                claim.setStatus("PROCESSING");
                claim.setOwnerId(ownerId);
                claim.setFencingVersion(fencingVersion);
                claim.setLeaseUntil(new Date(System.currentTimeMillis() + restoreLeaseSeconds * 1000L));
                claim.setBeforeStateJson(serializeBindings(bindings));
                restoreLogRepository.saveAndFlush(claim);
                return true;
            }

            CompensationRestoreLog claim = existing.get();
            if (!projectId.equals(claim.getProjectId())) {
                throw new IllegalArgumentException(
                        "Compensation event " + eventId + " is already bound to project " + claim.getProjectId()
                                + ", cannot restore project " + projectId);
            }
            if (claim.getBeforeStateJson() != null
                    && !bindingsEqualPersisted(claim.getBeforeStateJson(), bindings)) {
                throw new IllegalArgumentException(
                        "Compensation event " + eventId + " was claimed with different bindings");
            }
            if ("PROCESSING".equals(claim.getStatus()) && claim.getLeaseUntil() != null
                    && claim.getLeaseUntil().after(new Date())) {
                return false;
            }
            if (claim.getFencingVersion() != null && fencingVersion != null
                    && claim.getFencingVersion() >= fencingVersion) {
                return false;
            }

            Date now = new Date();
            return restoreLogRepository.takeOverClaim(
                    eventId, "PROCESSING", "FAILED", now,
                    new Date(now.getTime() + restoreLeaseSeconds * 1000L),
                    ownerId, fencingVersion) == 1;
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateKeyViolation(e)) {
                log.debug("Restore claim lost unique insert race: eventId={}", eventId);
                return false;
            }
            log.error("Restore claim failed due to unexpected DB integrity error: eventId={}", eventId, e);
            throw e;
        } catch (ObjectOptimisticLockingFailureException e) {
            log.debug("Restore claim lost optimistic-lock race: eventId={}", eventId);
            return false;
        }
    }

    /**
     * 判斷 DataIntegrityViolationException 是否為明確的 unique key conflict（SQLState 23505）。
     * 僅此類錯誤可視為「eventId 重複認領」；其他 integrity 失敗必須向外傳播。
     */
    private boolean isDuplicateKeyViolation(DataIntegrityViolationException e) {
        Throwable cause = e.getMostSpecificCause();
        if (cause instanceof ConstraintViolationException cve) {
            return "23505".equals(cve.getSQLState());
        }
        if (cause instanceof SQLException sqlEx) {
            return "23505".equals(sqlEx.getSQLState());
        }
        return false;
    }

    @Override
    public boolean verifyFencingHeld(UUID eventId, String ownerId, Long fencingVersion) {
        CompensationRestoreLog current = restoreLogRepository.findByIdForUpdate(eventId).orElse(null);
        if (current == null || !ownerId.equals(current.getOwnerId())
                || !fencingVersion.equals(current.getFencingVersion())) {
            log.warn("Fencing token superseded before destructive restore, abort: eventId={}, currentOwner={}, currentFence={}",
                    eventId, current != null ? current.getOwnerId() : "missing",
                    current != null ? current.getFencingVersion() : "missing");
            return false;
        }
        return true;
    }

    /**
     * 序列化還原目標綁定清單為 JSON（空清單序列化為 []，null 視為空清單）。
     */
    private String serializeBindings(List<BindingSnapshot> bindings) {
        try {
            return objectMapper.writeValueAsString(bindings == null ? List.of() : bindings);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize restore bindings", e);
        }
    }

    /**
     * 以持久化的 before-state JSON 與本次請求的 bindings 比對（以 Set 比較忽略順序）。
     */
    private boolean bindingsEqualPersisted(String persistedJson, List<BindingSnapshot> bindings) {
        try {
            List<BindingSnapshot> persisted = objectMapper.readValue(persistedJson, new TypeReference<>() {
            });
            return new HashSet<>(persisted).equals(new HashSet<>(bindings == null ? List.of() : bindings));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize persisted restore bindings", e);
        }
    }
}
