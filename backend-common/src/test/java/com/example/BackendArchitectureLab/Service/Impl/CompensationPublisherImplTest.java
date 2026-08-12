package com.example.BackendArchitectureLab.Service.Impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompensationPublisherImplTest {

    private static final String TOPIC = "transaction-compensation";

    @Mock
    private KafkaTemplate<String, CompensationEvent> compensationKafkaTemplate;

    private CompensationPublisherImpl publisher;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        publisher = new CompensationPublisherImpl();
        ReflectionTestUtils.setField(publisher, "compensationKafkaTemplate", compensationKafkaTemplate);
        ReflectionTestUtils.setField(publisher, "serviceName", "competency-service");

        Logger logger = (Logger) LoggerFactory.getLogger(CompensationPublisherImpl.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(CompensationPublisherImpl.class);
        logger.detachAppender(logAppender);
    }

    @Test
    void publish_ShouldSendEventToCompensationTopic() {
        UUID transactionId = UUID.randomUUID();
        CompensationEvent event = newEventWithStatus(transactionId, CompensationStatus.TRANSACTION_STARTED);
        when(compensationKafkaTemplate.send(TOPIC, transactionId.toString(), event))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        publisher.publish(event);

        verify(compensationKafkaTemplate).send(TOPIC, transactionId.toString(), event);
    }

    @Test
    void publish_ShouldNotLogError_whenAsyncSendSucceeds() {
        UUID transactionId = UUID.randomUUID();
        CompensationEvent event = newEventWithStatus(transactionId, CompensationStatus.COMMITTED);
        when(compensationKafkaTemplate.send(TOPIC, transactionId.toString(), event))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        publisher.publish(event);

        assertTrue(logAppender.list.stream().noneMatch(e -> e.getLevel().toString().equals("ERROR")));
    }

    @Test
    void publish_ShouldNotThrow_whenAsyncSendFails_AndLogError() {
        UUID transactionId = UUID.randomUUID();
        CompensationEvent event = newEventWithStatus(transactionId, CompensationStatus.COMMITTED);
        CompletableFuture<SendResult<String, CompensationEvent>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("broker unreachable"));
        when(compensationKafkaTemplate.send(TOPIC, transactionId.toString(), event))
                .thenReturn(failedFuture);

        assertDoesNotThrow(() -> publisher.publish(event));

        verify(compensationKafkaTemplate).send(TOPIC, transactionId.toString(), event);
        boolean loggedError = logAppender.list.stream()
                .anyMatch(e -> e.getLevel().toString().equals("ERROR")
                        && e.getFormattedMessage().contains("非同步發佈失敗"));
        assertTrue(loggedError, "非同步失敗應以 ERROR 層級記錄");
    }

    @Test
    void publish_ShouldNotThrow_whenKafkaSendFailsSynchronously() {
        UUID transactionId = UUID.randomUUID();
        CompensationEvent event = newEventWithStatus(transactionId, CompensationStatus.COMMITTED);
        doThrow(new RuntimeException("kafka down")).when(compensationKafkaTemplate)
                .send(anyString(), anyString(), any(CompensationEvent.class));

        assertDoesNotThrow(() -> publisher.publish(event));

        boolean loggedWarning = logAppender.list.stream()
                .anyMatch(e -> e.getLevel().toString().equals("WARN")
                        && e.getFormattedMessage().contains("同步"));
        assertTrue(loggedWarning, "同步失敗應以 WARN 層級記錄");
    }

    @Test
    void publishTransactionStarted_ShouldBuildEventWithStartedStatusAndEventId() {
        UUID transactionId = UUID.randomUUID();
        Map<String, Object> state = Map.of("projectId", "p1", "memberCount", 2);

        publisher.publishTransactionStarted(transactionId, CompensationAction.PROJECT_MEMBER_SKILLS_REBIND, state);

        CompensationEvent sent = captureSentEvent();
        assertEquals(transactionId, sent.getTransactionId());
        assertEquals("competency-service", sent.getServiceName());
        assertEquals(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND, sent.getAction());
        assertEquals(CompensationStatus.TRANSACTION_STARTED, sent.getStatus());
        assertEquals(state, sent.getBeforeState());
        assertNull(sent.getErrorMessage());
        assertNotNull(sent.getTimestamp());
        assertEventMetadata(sent);
    }

    @Test
    void publishCommitted_ShouldBuildEventWithCommittedStatus() {
        UUID transactionId = UUID.randomUUID();
        Map<String, Object> state = Map.of("projectId", "p1", "memberCount", 2);

        publisher.publishCommitted(transactionId, CompensationAction.PROJECT_MEMBER_SKILLS_REBIND, state);

        CompensationEvent sent = captureSentEvent();
        assertEquals(CompensationStatus.COMMITTED, sent.getStatus());
        assertEquals(state, sent.getBeforeState());
        assertNull(sent.getErrorMessage());
        assertEventMetadata(sent);
    }

    @Test
    void publishFailed_ShouldIncludeErrorMessage() {
        UUID transactionId = UUID.randomUUID();
        Map<String, Object> state = Map.of("projectId", "p1");

        publisher.publishFailed(transactionId, CompensationAction.PROJECT_MEMBER_SKILLS_REBIND, state, "User not found: xxx");

        CompensationEvent sent = captureSentEvent();
        assertEquals(CompensationStatus.FAILED, sent.getStatus());
        assertEquals("User not found: xxx", sent.getErrorMessage());
        assertEventMetadata(sent);
    }

    @Test
    void publishCompensated_ShouldBuildEventWithCompensatedStatus() {
        UUID transactionId = UUID.randomUUID();
        Map<String, Object> state = Map.of("projectId", "p1");

        publisher.publishCompensated(transactionId, CompensationAction.PROJECT_MEMBER_SKILLS_REBIND, state);

        CompensationEvent sent = captureSentEvent();
        assertEquals(CompensationStatus.COMPENSATED, sent.getStatus());
        assertEventMetadata(sent);
    }

    private CompensationEvent newEventWithStatus(UUID transactionId, String status) {
        return CompensationEvent.builder()
                .eventId(UUID.randomUUID())
                .eventVersion(1)
                .transactionId(transactionId)
                .serviceName("competency-service")
                .action(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND)
                .status(status)
                .beforeState(Map.of())
                .timestamp(Instant.now())
                .build();
    }

    private void assertEventMetadata(CompensationEvent sent) {
        assertNotNull(sent.getEventId(), "每個事件都應有唯一 eventId");
        assertEquals(1, sent.getEventVersion(), "事件 schema 版本應為 1");
    }

    private CompensationEvent captureSentEvent() {
        ArgumentCaptor<CompensationEvent> captor = ArgumentCaptor.forClass(CompensationEvent.class);
        verify(compensationKafkaTemplate).send(eq(TOPIC), anyString(), captor.capture());
        return captor.getValue();
    }
}