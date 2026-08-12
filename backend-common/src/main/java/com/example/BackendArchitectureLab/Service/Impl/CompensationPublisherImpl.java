package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Service.ICompensationPublisher;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * CompensationPublisherImpl - 交易補償事件發佈實作，
 * 僅在有 compensationKafkaTemplate bean 的服務（alert-service / competency-service）中建立。
 */
@Slf4j
@Component
@ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
@ConditionalOnBean(name = "compensationKafkaTemplate")
public class CompensationPublisherImpl implements ICompensationPublisher {

    private static final String TOPIC = "transaction-compensation";

    @Autowired
    private KafkaTemplate<String, CompensationEvent> compensationKafkaTemplate;

    @Override
    public CompletableFuture<Void> publish(CompensationEvent event) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            compensationKafkaTemplate.send(TOPIC, event.getTransactionId().toString(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("補償事件非同步發佈失敗: transactionId={}, action={}, status={}, cause={}",
                                    event.getTransactionId(), event.getAction(), event.getStatus(), ex.toString());
                            future.completeExceptionally(ex);
                        } else {
                            log.debug("補償事件已發佈: transactionId={}, action={}, status={}",
                                    event.getTransactionId(), event.getAction(), event.getStatus());
                            future.complete(null);
                        }
                    });
            return future;
        } catch (Exception e) {
            log.warn("補償事件發佈失敗(同步): transactionId={}, cause={}", event.getTransactionId(), e.toString());
            return CompletableFuture.failedFuture(e);
        }
    }
}
