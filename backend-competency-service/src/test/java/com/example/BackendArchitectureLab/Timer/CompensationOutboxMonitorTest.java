package com.example.BackendArchitectureLab.Timer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.BackendArchitectureLab.Entity.CompensationOutboxEvent;
import com.example.BackendArchitectureLab.Repository.CompensationOutboxEventRepository;
import com.example.BackendArchitectureLab.Vo.CompensationOutboxDeliveryStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompensationOutboxMonitorTest {

    @Mock
    private CompensationOutboxEventRepository outboxEventRepository;

    @InjectMocks
    private CompensationOutboxMonitor compensationOutboxMonitor;

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        Logger logger = (Logger) LoggerFactory.getLogger(CompensationOutboxMonitor.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(CompensationOutboxMonitor.class);
        logger.detachAppender(appender);
    }

    @Test
    void monitorDeadEvents_shouldLogError_whenDeadEventsExist() {
        CompensationOutboxEvent deadEvent = new CompensationOutboxEvent();
        deadEvent.setEventId(UUID.randomUUID());
        deadEvent.setTransactionId(UUID.randomUUID());
        deadEvent.setDeliveryStatus(CompensationOutboxDeliveryStatus.DEAD);
        deadEvent.setAttemptCount(5);
        deadEvent.setErrorMessage("broker unreachable");
        when(outboxEventRepository.findByDeliveryStatus(CompensationOutboxDeliveryStatus.DEAD))
                .thenReturn(List.of(deadEvent));

        compensationOutboxMonitor.monitorDeadEvents();

        List<ILoggingEvent> errorLogs = appender.list.stream()
                .filter(event -> event.getLevel().toString().equals("ERROR"))
                .toList();
        assertEquals(1, errorLogs.size());
        assertTrue(errorLogs.get(0).getFormattedMessage().contains(deadEvent.getEventId().toString()));
        assertTrue(errorLogs.get(0).getFormattedMessage().contains("attemptCount=5"));
        assertTrue(errorLogs.get(0).getFormattedMessage().contains("broker unreachable"));
    }

    @Test
    void monitorDeadEvents_shouldNotLog_whenNoDeadEvents() {
        when(outboxEventRepository.findByDeliveryStatus(CompensationOutboxDeliveryStatus.DEAD))
                .thenReturn(List.of());

        compensationOutboxMonitor.monitorDeadEvents();

        long errorCount = appender.list.stream()
                .filter(event -> event.getLevel().toString().equals("ERROR"))
                .count();
        assertEquals(0, errorCount);
    }
}
