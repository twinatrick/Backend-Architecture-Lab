package com.example.BackendArchitectureLab.Timer;

import com.example.BackendArchitectureLab.DataAccess.IExternalSyncCommandDataAccess;
import com.example.BackendArchitectureLab.Entity.ExternalSyncCommand;
import com.example.BackendArchitectureLab.Service.IExternalSyncCommandService;
import com.example.BackendArchitectureLab.Service.IExternalSyncService;
import com.example.BackendArchitectureLab.Vo.CompensationOutboxDeliveryStatus;
import com.example.BackendArchitectureLab.Vo.ExternalSyncCommandPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ExternalSyncWorker - 外部同步 durable command 執行工作者（與寫入 Service 分離）。
 * <p>
 * 定期批次執行尚未同步的命令：先原子領取（PENDING/FAILED 到期或 PROCESSING 租約過期 → PROCESSING），
 * 呼叫 {@link IExternalSyncService#syncProjectMemberSkills}，成功後標記 SENT；
 * 失敗依指數退避排下次重試，超過最大次數轉為 DEAD 並觸發補償閉環（COMPENSATION_REQUIRED）。
 * 此機制消除「本地交易已 commit 但 JVM 在外部同步前 crash」導致同步永不執行的 crash window：
 * 命令與業務交易同 commit，因此只要命令存在，外部同步遲早會被執行。
 * 批次採序列執行（外部 HTTP 呼叫由外部 adapter 自管並行度），不引入額外的並行計算。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalSyncWorker {

    private static final long[] DEFAULT_BACKOFF_SECONDS = {5L, 15L, 30L, 60L, 300L};

    @Value("${external-sync.max-attempts:5}")
    private int maxAttempts;

    @Value("${external-sync.lease-seconds:300}")
    private long leaseSeconds;

    @Value("${external-sync.batch-size:20}")
    private int batchSize;

    @Value("${external-sync.operation-timeout-seconds:10}")
    private long operationTimeoutSeconds;

    @Value("${external-sync.backoff-seconds:5,15,30,60,300}")
    private List<Long> backoffSeconds;

    private final IExternalSyncCommandDataAccess commandRepository;
    private final IExternalSyncService externalSyncService;
    private final ObjectMapper objectMapper;
    private final IExternalSyncCommandService externalSyncCommandService;

    /**
     * 啟動時驗證租約組態不變式：
     * 本批次採序列執行，最多 batch-size 筆，每筆外部呼叫最長 operation-timeout-seconds 秒；
     * lease-seconds 若小於等於 batch-size * operation-timeout-seconds，最壞情況可能在批次完成前
     * 就被其他實例接管，造成重複執行。此處以 worst-case 批次總時間 fail-fast，確保租約足以覆蓋整批執行。
     */
    @PostConstruct
    void validateConfiguration() {
        long worstCaseSeconds = (long) batchSize * operationTimeoutSeconds;
        if (leaseSeconds <= worstCaseSeconds) {
            throw new IllegalStateException(
                    "external-sync.lease-seconds (" + leaseSeconds
                            + ") must be greater than batch-size * operation-timeout-seconds (" + worstCaseSeconds
                            + ") so a serial batch can complete before lease expiry");
        }
    }

    /**
     * 批次執行尚未同步的命令（預設每 5 秒執行一次）。
     */
    @Scheduled(fixedDelayString = "${external-sync.flush-delay-ms:5000}")
    public void flushPendingCommands() {
        if (!externalSyncCommandService.isEnabled()) {
            return;
        }
        List<ExternalSyncCommand> pending = commandRepository.findPendingDue(
                List.of(CompensationOutboxDeliveryStatus.PENDING,
                        CompensationOutboxDeliveryStatus.FAILED,
                        CompensationOutboxDeliveryStatus.PROCESSING),
                CompensationOutboxDeliveryStatus.PROCESSING,
                PageRequest.of(0, batchSize));
        for (ExternalSyncCommand command : pending) {
            processOne(command);
        }
    }

    /**
     * 執行單一命令：原子領取 → 重新讀取最新狀態 → 反序列化 payload → 呼叫外部同步 → 原子標記 SENT。
     * 任何例外皆由 handleExecutionFailure 以帶 ownerId + fencingVersion 的原子 UPDATE 標記 FAILED/DEAD，不向上拋出。
     */
    private void processOne(ExternalSyncCommand command) {
        Date now = new Date();
        String ownerId = UUID.randomUUID().toString();
        int claimed = commandRepository.claimCommand(
                command.getId(),
                List.of(CompensationOutboxDeliveryStatus.PENDING,
                        CompensationOutboxDeliveryStatus.FAILED,
                        CompensationOutboxDeliveryStatus.PROCESSING),
                CompensationOutboxDeliveryStatus.PROCESSING,
                ownerId,
                now,
                new Date(now.getTime() + leaseSeconds * 1000L));
        if (claimed == 0) {
            return;
        }
        // claim 後重新讀取最新狀態（attemptCount 與 fencingVersion 已由 claim 原子遞增），避免使用陳舊資料
        ExternalSyncCommand fresh = commandRepository.findById(command.getId()).orElse(null);
        if (fresh == null) {
            return;
        }

        Long fencingVersion = fresh.getFencingVersion();
        ExternalSyncCommandPayload payload;
        try {
            payload = objectMapper.readValue(fresh.getPayload(), ExternalSyncCommandPayload.class);
        } catch (Exception e) {
            handleExecutionFailure(fresh, fresh.getTransactionId(), null, ownerId, fencingVersion, e);
            return;
        }
        try {
            externalSyncService.syncProjectMemberSkills(fresh.getProjectId(), payload.getMemberSkillsMap());
            commandRepository.markSent(fresh.getId(),
                    ownerId,
                    fencingVersion,
                    CompensationOutboxDeliveryStatus.SENT,
                    CompensationOutboxDeliveryStatus.PROCESSING,
                    new Date());
        } catch (Exception e) {
            handleExecutionFailure(fresh, fresh.getTransactionId(), payload.getBeforeState(), ownerId, fencingVersion, e);
        }
    }

    /**
     * 執行失敗處理：以帶 ownerId + fencingVersion 的原子 UPDATE 標記狀態；未達上限 → FAILED 並排下次重試；已達上限時
     * 委託 {@link IExternalSyncCommandService#markDeadAndEnqueueCompensation} 在同一交易內
     * 標記 DEAD 並寫入 FAILED + COMPENSATION_REQUIRED，確保補償請求與 DEAD 狀態原子化、
     * 不會在「DEAD 已提交但補償寫入失敗」時永久遺失補償事件，且不會因 stale token 誤發補償。
     * attemptCount 於 claim 時遞增，此處以現值判斷。
     */
    private void handleExecutionFailure(ExternalSyncCommand command, UUID transactionId,
                                        Map<String, Object> beforeState, String ownerId,
                                        Long fencingVersion, Exception e) {
        String errorMessage = truncate(e != null ? e.getMessage() : null);
        int attempt = command.getAttemptCount();
        if (attempt >= maxAttempts) {
            externalSyncCommandService.markDeadAndEnqueueCompensation(
                    command.getId(), ownerId, fencingVersion, transactionId, beforeState, errorMessage);
            log.error("外部同步已達最大重試次數，轉為 DEAD 並觸發補償: commandId={}, transactionId={}, attempt={}, ownerId={}, fencingVersion={}, cause={}",
                    command.getId(), transactionId, attempt, ownerId, fencingVersion, e != null ? e.toString() : "null");
        } else {
            long backoff = resolveBackoffSeconds(attempt);
            int affected = commandRepository.markFailed(command.getId(),
                    ownerId,
                    fencingVersion,
                    CompensationOutboxDeliveryStatus.FAILED,
                    CompensationOutboxDeliveryStatus.PROCESSING,
                    errorMessage,
                    new Date(System.currentTimeMillis() + backoff * 1000L));
            if (affected > 0) {
                log.warn("外部同步失敗，排定重試: commandId={}, transactionId={}, attempt={}, ownerId={}, fencingVersion={}, nextAttemptIn={}s, cause={}",
                        command.getId(), transactionId, attempt, ownerId, fencingVersion, backoff, e != null ? e.toString() : "null");
            } else {
                log.warn("外部同步失敗但租約已被接管，略過 markFailed: commandId={}, transactionId={}, ownerId={}, fencingVersion={}",
                        command.getId(), transactionId, ownerId, fencingVersion);
            }
        }
    }

    private long resolveBackoffSeconds(int attempt) {
        List<Long> backoffs = (backoffSeconds == null || backoffSeconds.isEmpty())
                ? List.of(DEFAULT_BACKOFF_SECONDS[0], DEFAULT_BACKOFF_SECONDS[1], DEFAULT_BACKOFF_SECONDS[2],
                DEFAULT_BACKOFF_SECONDS[3], DEFAULT_BACKOFF_SECONDS[4])
                : backoffSeconds;
        int index = Math.max(0, Math.min(attempt - 1, backoffs.size() - 1));
        return backoffs.get(index);
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 1024 ? message.substring(0, 1024) : message;
    }
}