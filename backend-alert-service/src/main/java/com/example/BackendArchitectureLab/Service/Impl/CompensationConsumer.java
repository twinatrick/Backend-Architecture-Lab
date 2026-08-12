package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Entity.CompensationEventLog;
import com.example.BackendArchitectureLab.Repository.CompensationEventLogRepository;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Date;

@Slf4j
@Service
public class CompensationConsumer {

    @Autowired
    private CompensationEventLogRepository eventLogRepository;

    @KafkaListener(topics = "transaction-compensation", containerFactory = "compensationKafkaListenerContainerFactory")
    public void handleCompensation(CompensationEvent event) {
        log.info("Received compensation event: action={}, status={}, transactionId={}, eventId={}, eventVersion={}",
                event.getAction(), event.getStatus(), event.getTransactionId(),
                event.getEventId(), event.getEventVersion());

        if (event.getEventId() == null) {
            log.warn("Compensation event without eventId ignored (unsupported legacy event): transactionId={}",
                    event.getTransactionId());
            return;
        }

        // 冪等處理：eventId 已處理過的事件直接忽略
        if (eventLogRepository.existsByEventId(event.getEventId())) {
            log.debug("Duplicate compensation event ignored: eventId={}", event.getEventId());
            return;
        }
        recordReceivedEvent(event);

        if (CompensationStatus.COMPENSATED.equals(event.getStatus())) {
            executeCompensation(event);
        }
    }

    private void recordReceivedEvent(CompensationEvent event) {
        CompensationEventLog eventLog = new CompensationEventLog();
        eventLog.setEventId(event.getEventId());
        eventLog.setTransactionId(event.getTransactionId());
        eventLog.setStatus(event.getStatus());
        eventLog.setReceivedAt(new Date());
        eventLogRepository.save(eventLog);
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