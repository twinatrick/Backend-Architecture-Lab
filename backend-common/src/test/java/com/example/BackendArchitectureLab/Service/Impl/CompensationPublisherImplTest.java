package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompensationPublisherImplTest {

    private static final String TOPIC = "transaction-compensation";

    @Mock
    private KafkaTemplate<String, CompensationEvent> compensationKafkaTemplate;

    private CompensationPublisherImpl publisher;

    @BeforeEach
    void setUp() {
        publisher = new CompensationPublisherImpl();
        ReflectionTestUtils.setField(publisher, "compensationKafkaTemplate", compensationKafkaTemplate);
        ReflectionTestUtils.setField(publisher, "serviceName", "competency-service");
    }

    @Test
    void publish_ShouldSendEventToCompensationTopic() {
        UUID transactionId = UUID.randomUUID();
        CompensationEvent event = new CompensationEvent(
                transactionId, "competency-service", CompensationAction.PROJECT_MEMBER_SKILLS_REBIND,
                CompensationStatus.SAVE_POINT, Map.of(), null, null, java.time.Instant.now());

        publisher.publish(event);

        verify(compensationKafkaTemplate).send(TOPIC, transactionId.toString(), event);
    }

    @Test
    void publish_ShouldNotThrow_whenKafkaSendFails() {
        UUID transactionId = UUID.randomUUID();
        CompensationEvent event = new CompensationEvent(
                transactionId, "competency-service", CompensationAction.PROJECT_MEMBER_SKILLS_REBIND,
                CompensationStatus.SAVE_POINT, Map.of(), null, null, java.time.Instant.now());
        doThrow(new RuntimeException("kafka down")).when(compensationKafkaTemplate)
                .send(anyString(), anyString(), any(CompensationEvent.class));

        assertDoesNotThrow(() -> publisher.publish(event));
    }

    @Test
    void publishSavePoint_ShouldBuildEventWithSavePointStatus() {
        UUID transactionId = UUID.randomUUID();
        Map<String, Object> state = Map.of("projectId", "p1", "memberCount", 2);

        publisher.publishSavePoint(transactionId, CompensationAction.PROJECT_MEMBER_SKILLS_REBIND, state);

        CompensationEvent sent = captureSentEvent();
        assertEquals(transactionId, sent.getTransactionId());
        assertEquals("competency-service", sent.getServiceName());
        assertEquals(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND, sent.getAction());
        assertEquals(CompensationStatus.SAVE_POINT, sent.getStatus());
        assertEquals(state, sent.getBeforeState());
        assertNull(sent.getErrorMessage());
        assertNotNull(sent.getTimestamp());
    }

    @Test
    void publishCommitted_ShouldBuildEventWithCommittedStatus() {
        UUID transactionId = UUID.randomUUID();
        Map<String, Object> state = Map.of("projectId", "p1", "memberCount", 2);

        publisher.publishCommitted(transactionId, CompensationAction.PROJECT_MEMBER_SKILLS_REBIND, state);

        CompensationEvent sent = captureSentEvent();
        assertEquals(CompensationStatus.COMMITTED, sent.getStatus());
        assertNull(sent.getErrorMessage());
    }

    @Test
    void publishFailed_ShouldIncludeErrorMessage() {
        UUID transactionId = UUID.randomUUID();
        Map<String, Object> state = Map.of("projectId", "p1");

        publisher.publishFailed(transactionId, CompensationAction.PROJECT_MEMBER_SKILLS_REBIND, state, "User not found: xxx");

        CompensationEvent sent = captureSentEvent();
        assertEquals(CompensationStatus.FAILED, sent.getStatus());
        assertEquals("User not found: xxx", sent.getErrorMessage());
    }

    @Test
    void publishCompensated_ShouldBuildEventWithCompensatedStatus() {
        UUID transactionId = UUID.randomUUID();
        Map<String, Object> state = Map.of("projectId", "p1");

        publisher.publishCompensated(transactionId, CompensationAction.PROJECT_MEMBER_SKILLS_REBIND, state);

        CompensationEvent sent = captureSentEvent();
        assertEquals(CompensationStatus.COMPENSATED, sent.getStatus());
    }

    private CompensationEvent captureSentEvent() {
        ArgumentCaptor<CompensationEvent> captor = ArgumentCaptor.forClass(CompensationEvent.class);
        verify(compensationKafkaTemplate).send(eq(TOPIC), anyString(), captor.capture());
        return captor.getValue();
    }
}
