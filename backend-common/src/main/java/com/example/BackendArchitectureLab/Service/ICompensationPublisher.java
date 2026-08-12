package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;

import java.util.Map;
import java.util.UUID;

/**
 * ICompensationPublisher - 交易補償事件的發佈抽象介面（DIP），
 * 業務服務僅依賴此介面發佈補償事件，不需直接操作 Kafka。
 */
public interface ICompensationPublisher {

    void publish(CompensationEvent event);

    void publishSavePoint(UUID transactionId, CompensationAction action, Map<String, Object> state);

    void publishCommitted(UUID transactionId, CompensationAction action, Map<String, Object> state);

    void publishFailed(UUID transactionId, CompensationAction action, Map<String, Object> state, String errorMessage);

    void publishCompensated(UUID transactionId, CompensationAction action, Map<String, Object> state);
}
