package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;

import java.util.concurrent.CompletableFuture;

/**
 * ICompensationPublisher - 交易補償事件的發佈抽象介面（DIP）。
 * 僅供 Outbox Worker（CompensationOutboxServiceImpl）使用，業務服務不直接依賴此介面；
 * 呼叫端負責以 CompensationEvent.builder() 組裝事件（含 eventId / eventVersion）。
 */
public interface ICompensationPublisher {

    /**
     * 發佈事件並回傳發佈結果（等待 Kafka ACK）。
     * 若發佈失敗，回傳的 Future 會以例外完成（failure 語意由呼叫端決定重試策略）。
     */
    CompletableFuture<Void> publish(CompensationEvent event);
}
