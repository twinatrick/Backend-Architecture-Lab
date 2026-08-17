package com.example.BackendArchitectureLab.Timer;

import com.example.BackendArchitectureLab.DataAccess.IExternalSyncCommandDataAccess;
import com.example.BackendArchitectureLab.Entity.ExternalSyncCommand;
import com.example.BackendArchitectureLab.Service.ICompensationOutboxService;
import com.example.BackendArchitectureLab.Service.IExternalSyncCommandService;
import com.example.BackendArchitectureLab.Service.IExternalSyncService;
import com.example.BackendArchitectureLab.Vo.CompensationOutboxDeliveryStatus;
import com.example.BackendArchitectureLab.Vo.ExternalSyncCommandPayload;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
public class ExternalSyncWorker {

    private static final long[] DEFAULT_BACKOFF_SECONDS = {5L, 15L, 30L, 60L, 300L};

    @Value("${external-sync.max-attempts:5}")
    private int maxAttempts;

    @Value("${external-sync.lease-seconds:300}")
    private long leaseSeconds;

    @Value("${external-sync.batch-size:20}")
    private int batchSize;

    @Value("${external-sync.backoff-seconds:5,15,30,60,300}")
    private List<Long> backoffSeconds;

    @Autowired
    private IExternalSyncCommandDataAccess commandRepository;

    @Autowired
    private IExternalSyncService externalSyncService;

    @Autowired
    private ICompensationOutboxService compensationOutboxService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IExternalSyncCommandService externalSyncCommandService;

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
     * 任何例外皆由 handleExecutionFailure 以原子 UPDATE 標記 FAILED/DEAD，不向上拋出。
     */
    private void processOne(ExternalSyncCommand command) {
        Date now = new Date();
        int claimed = commandRepository.claimCommand(
                command.getId(),
                List.of(CompensationOutboxDeliveryStatus.PENDING,
                        CompensationOutboxDeliveryStatus.FAILED,
                        CompensationOutboxDeliveryStatus.PROCESSING),
                CompensationOutboxDeliveryStatus.PROCESSING,
                now,
                new Date(now.getTime() + leaseSeconds * 1000L));
        if (claimed == 0) {
            return;
        }
        // claim 後重新讀取最新狀態（attemptCount 已由 claim 原子遞增），避免使用陳舊資料
        ExternalSyncCommand fresh = commandRepository.findById(command.getId()).orElse(null);
        if (fresh == null) {
            return;
        }

        ExternalSyncCommandPayload payload;
        try {
            payload = objectMapper.readValue(fresh.getPayload(), ExternalSyncCommandPayload.class);
        } catch (Exception e) {
            handleExecutionFailure(fresh, fresh.getTransactionId(), null, e);
            return;
        }
        try {
            externalSyncService.syncProjectMemberSkills(fresh.getProjectId(), payload.getMemberSkillsMap());
            commandRepository.markSent(fresh.getId(),
                    CompensationOutboxDeliveryStatus.SENT,
                    CompensationOutboxDeliveryStatus.PROCESSING,
                    new Date());
        } catch (Exception e) {
            handleExecutionFailure(fresh, fresh.getTransactionId(), payload.getBeforeState(), e);
        }
    }

    /**
     * 執行失敗處理：以原子 UPDATE 標記狀態；未達上限 → FAILED 並排下次重試；已達上限 → DEAD
     * 並觸發補償閉環（REQUIRES_NEW 寫入 FAILED + COMPENSATION_REQUIRED，確保補償請求可靠持久化）。
     * attemptCount 於 claim 時遞增，此處以現值判斷。
     */
    private void handleExecutionFailure(ExternalSyncCommand command, UUID transactionId,
                                        Map<String, Object> beforeState, Exception e) {
        String errorMessage = truncate(e.getMessage());
        int attempt = command.getAttemptCount();
        if (attempt >= maxAttempts) {
            commandRepository.markDead(command.getId(),
                    CompensationOutboxDeliveryStatus.DEAD,
                    CompensationOutboxDeliveryStatus.PROCESSING,
                    errorMessage);
            compensationOutboxService.enqueueFailureAndCompensationRequired(
                    transactionId, CompensationAction.PROJECT_MEMBER_SKILLS_REBIND, beforeState, errorMessage);
            log.error("外部同步已達最大重試次數，轉為 DEAD 並觸發補償: commandId={}, transactionId={}, attempt={}, cause={}",
                    command.getId(), transactionId, attempt, e.toString());
        } else {
            long backoff = resolveBackoffSeconds(attempt);
            commandRepository.markFailed(command.getId(),
                    CompensationOutboxDeliveryStatus.FAILED,
                    CompensationOutboxDeliveryStatus.PROCESSING,
                    errorMessage,
                    new Date(System.currentTimeMillis() + backoff * 1000L));
            log.warn("外部同步失敗，排定重試: commandId={}, transactionId={}, attempt={}, nextAttemptIn={}s, cause={}",
                    command.getId(), transactionId, attempt, backoff, e.toString());
        }
    }

    private long resolveBackoffSeconds(int attempt) {
        List<Long> backoffs = (backoffSeconds == null || backoffSeconds.isEmpty())
                ? List.of(DEFAULT_BACKOFF_SECONDS[0], DEFAULT_BACKOFF_SECONDS[1], DEFAULT_BACKOFF_SECONDS[2],
                DEFAULT_BACKOFF_SECONDS[3], DEFAULT_BACKOFF_SECONDS[4])
                : backoffSeconds;
        return backoffs.get(Math.min(attempt - 1, backoffs.size() - 1));
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 1024 ? message.substring(0, 1024) : message;
    }
}