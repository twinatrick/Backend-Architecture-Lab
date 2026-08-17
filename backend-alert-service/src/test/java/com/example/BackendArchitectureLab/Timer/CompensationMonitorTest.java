package com.example.BackendArchitectureLab.Timer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.BackendArchitectureLab.Entity.CompensationEventLog;
import com.example.BackendArchitectureLab.DataAccess.ICompensationEventLogDataAccess;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEventLogStatus;
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
class CompensationMonitorTest {

    @Mock
    private ICompensationEventLogDataAccess eventLogRepository;

    @InjectMocks
    private CompensationMonitor compensationMonitor;

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        Logger logger = (Logger) LoggerFactory.getLogger(CompensationMonitor.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(CompensationMonitor.class);
        logger.detachAppender(appender);
    }

    @Test
    void monitorFailedEvents_shouldLogError_whenDeadEventsExist() {
        CompensationEventLog deadLog = new CompensationEventLog();
        deadLog.setEventId(UUID.randomUUID());
        deadLog.setTransactionId(UUID.randomUUID());
        deadLog.setStatus(CompensationEventLogStatus.DEAD);
        deadLog.setAttemptCount(5);
        deadLog.setLastError("Kafka 連線失敗");
        when(eventLogRepository.findTop20ByStatusOrderByUpdatedTimeDesc(CompensationEventLogStatus.DEAD)).thenReturn(List.of(deadLog));

        compensationMonitor.monitorFailedEvents();

        List<ILoggingEvent> errorLogs = appender.list.stream()
                .filter(event -> event.getLevel().toString().equals("ERROR"))
                .toList();
        assertEquals(1, errorLogs.size());
        assertTrue(errorLogs.get(0).getFormattedMessage().contains(deadLog.getEventId().toString()));
        assertTrue(errorLogs.get(0).getFormattedMessage().contains("attemptCount=5"));
        assertTrue(errorLogs.get(0).getFormattedMessage().contains("Kafka 連線失敗"));
    }

    @Test
    void monitorFailedEvents_shouldNotLog_whenNoDeadEvents() {
        when(eventLogRepository.findTop20ByStatusOrderByUpdatedTimeDesc(CompensationEventLogStatus.DEAD)).thenReturn(List.of());

        compensationMonitor.monitorFailedEvents();

        long errorCount = appender.list.stream()
                .filter(event -> event.getLevel().toString().equals("ERROR"))
                .count();
        assertEquals(0, errorCount);
    }
}