package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Event.AlarmEvent;
import com.example.BackendArchitectureLab.Service.IAlarmPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlarmServiceTest {

    @Mock
    private IAlarmPublisher alarmPublisher;

    @InjectMocks
    private AlarmService alarmService;

    @Test
    void processAlarm_whenPublishSucceeds_shouldNotModifyMessage() {
        List<AlarmEvent> messages = List.of(
                createAlarmEvent("ERROR", "test-source", "test message"));

        alarmService.processAlarm(messages);

        verify(alarmPublisher, times(1)).publish(messages);
        assertEquals("test message", messages.get(0).getMessage());
    }

    @Test
    void processAlarm_whenPublishThrowsException_shouldSetFailureMessage() {
        List<AlarmEvent> messages = List.of(
                createAlarmEvent("ERROR", "test-source", "original message"));
        doThrow(new RuntimeException("Kafka unavailable")).when(alarmPublisher).publish(messages);

        alarmService.processAlarm(messages);

        verify(alarmPublisher, times(1)).publish(messages);
        assertEquals("告警消息發送失敗：Kafka unavailable", messages.get(0).getMessage());
    }

    @Test
    void processAlarm_whenPublishThrowsRuntimeException_shouldHandleGracefully() {
        List<AlarmEvent> messages = List.of(
                createAlarmEvent("WARN", "api", "some warning"));
        doThrow(new RuntimeException("connection timeout")).when(alarmPublisher).publish(messages);

        assertDoesNotThrow(() -> alarmService.processAlarm(messages));

        assertTrue(messages.get(0).getMessage().contains("connection timeout"));
    }

    @Test
    void processAlarm_withMultipleMessages_shouldPublishAll() {
        List<AlarmEvent> messages = List.of(
                createAlarmEvent("ERROR", "src1", "msg1"),
                createAlarmEvent("WARN", "src2", "msg2"),
                createAlarmEvent("INFO", "src3", "msg3"));

        alarmService.processAlarm(messages);

        verify(alarmPublisher, times(1)).publish(messages);
    }

    @Test
    void processAlarm_whenPublishThrowsExceptionWithNullMessage_shouldHandleNullGetMessage() {
        List<AlarmEvent> messages = List.of(
                createAlarmEvent("ERROR", "test", "original"));
        RuntimeException ex = mock(RuntimeException.class);
        when(ex.getMessage()).thenReturn(null);
        doThrow(ex).when(alarmPublisher).publish(messages);

        alarmService.processAlarm(messages);

        assertEquals("告警消息發送失敗：null", messages.get(0).getMessage());
    }

    private AlarmEvent createAlarmEvent(String level, String source, String message) {
        AlarmEvent alarm = new AlarmEvent();
        alarm.setLevel(level);
        alarm.setSource(source);
        alarm.setMessage(message);
        return alarm;
    }
}
