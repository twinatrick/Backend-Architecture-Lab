package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;

import java.util.Map;
import java.util.UUID;

/**
 * ICompensationOutboxService - 交易補償事件的 Outbox 寫入抽象介面，
 * 事件與業務交易同 commit 持久化，再由 {@code Timer/CompensationOutboxWorker} 批次發佈至 Kafka，
 * 避免「DB 已 commit 但事件遺失」的可靠性缺口。
 */
public interface ICompensationOutboxService {

    /**
     * 在業務交易內寫入交易開始事件（與業務同 commit，rollback 時一併消失）
     */
    void enqueueTransactionStarted(UUID transactionId, CompensationAction action, Map<String, Object> state);

    /**
     * 與業務資料在同一交易內寫入交易完成事件（同 commit，rollback 時一併消失）
     */
    void enqueueCommitted(UUID transactionId, CompensationAction action, Map<String, Object> state);

    /**
     * 業務交易失敗後（rollback 完成）寫入失敗與補償請求事件。
     * <p>
     * 同一個交易內依序寫入 {@code FAILED}（記錄失敗事實）與
     * {@code COMPENSATION_REQUIRED}（補償請求，閉環：Consumer 委派 CompensationStrategy 執行補償），
     * 兩者要嘛一起 commit、要嘛一起 rollback，避免閉環斷裂。
     * 若呼叫端已有交易（如 ExternalSyncWorker 將命令標記 DEAD 的同一交易）則加入該交易，
     * 確保「DEAD 標記 + 補償請求」原子化；無交易上下文時自行開啟新交易。
     */
    void enqueueFailureAndCompensationRequired(UUID transactionId, CompensationAction action,
                                               Map<String, Object> state, String errorMessage);
}
