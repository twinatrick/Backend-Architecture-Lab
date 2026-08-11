package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CompensationConsumer {

    @KafkaListener(topics = "transaction-compensation", containerFactory = "compensationKafkaListenerContainerFactory")
    public void handleCompensation(CompensationEvent event) {
        log.info("Received compensation event: action={}, status={}, transactionId={}",
                event.getAction(), event.getStatus(), event.getTransactionId());

        if (CompensationStatus.COMPENSATED.equals(event.getStatus())) {
            executeCompensation(event);
        }
    }

    private void executeCompensation(CompensationEvent event) {
        log.warn("Executing compensation for transaction {} action {}",
                event.getTransactionId(), event.getAction());

        if (CompensationAction.PROJECT_MEMBER_SKILLS_REBIND.equals(event.getAction())) {
            compensateProjectMemberSkillsRebind(event);
        } else {
            log.warn("Unknown compensation action: {}", event.getAction());
        }
    }

    private void compensateProjectMemberSkillsRebind(CompensationEvent event) {
        log.info("Compensating PROJECT_MEMBER_SKILLS_REBIND: transactionId={}", event.getTransactionId());
        log.info("No compensation needed for this action as @Transactional handles rollback");
    }
}
