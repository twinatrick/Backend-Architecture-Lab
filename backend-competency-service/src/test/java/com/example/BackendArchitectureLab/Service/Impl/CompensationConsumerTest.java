package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Service.CompensationEventProcessor;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CompensationConsumerTest {

    @Mock
    private CompensationEventProcessor eventProcessor;

    @InjectMocks
    private CompensationConsumer compensationConsumer;

    private CompensationEvent event;

    @BeforeEach
    void setUp() {
        event = CompensationEvent.builder()
                .eventId(UUID.randomUUID())
                .eventVersion(1)
                .transactionId(UUID.randomUUID())
                .serviceName("competency-service")
                .action(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND)
                .status(CompensationStatus.COMPENSATION_REQUIRED)
                .beforeState(Map.of("key1", "value1"))
                .timestamp(Instant.now())
                .build();
    }

    @Test
    void handleCompensation_shouldDelegateToEventProcessor() {
        compensationConsumer.handleCompensation(event);

        verify(eventProcessor).process(event);
    }
}
