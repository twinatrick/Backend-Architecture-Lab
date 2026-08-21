package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;

import java.util.concurrent.CompletableFuture;

/**
 * ICompensationPublisher - 交易補償事件的發佈抽象介面（DIP）。
 * 僅供 Outbox Worker（CompensationOutboxWorker / CompensationOutboxServiceImpl）使用，業務服務不直接依賴此介面；
 * 呼叫端負責以 CompensationEvent.builder() 組裝事件（含 eventId / eventVersion）。
 *
 * <p>【投遞語意 (Delivery Semantics)】</p>
 * Outbox 機制提供「至少一次投遞 (At-Least-Once Delivery)」保證。
 * 在網路逾時 (ACK Timeout)、連線中斷或 Worker 重試情境下，底層發布可能已抵達 Broker，
 * 因此同一 eventId 可能會被重複發送；下游 Consumer 必須依據 eventId 實作等冪性去重 (Idempotency / Dedup)。
 */
public interface ICompensationPublisher {

    /**
     * 發佈事件並回傳發佈結果（等待 Kafka ACK）。
     * 若發佈失敗或 ACK 逾時，回傳的 Future 會以例外完成（failure 語意由呼叫端決定重試策略）。
     */
    CompletableFuture<Void> publish(CompensationEvent event);
}
