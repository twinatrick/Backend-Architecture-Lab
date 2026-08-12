package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;

import java.util.Map;
import java.util.UUID;

/**
 * ICompensationOutboxService - 交易補償事件的 Outbox 寫入抽象介面，
 * 事件與業務交易同 commit 持久化，再由排程批次發佈至 Kafka，
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
     * 業務交易失敗後寫入交易失敗事件（獨立新交易，含錯誤訊息）
     */
    void enqueueFailed(UUID transactionId, CompensationAction action, Map<String, Object> state, String errorMessage);

    /**
     * 批次發佈所有尚未發送的事件至 Kafka（由排程觸發）
     */
    void flushPendingEvents();
}