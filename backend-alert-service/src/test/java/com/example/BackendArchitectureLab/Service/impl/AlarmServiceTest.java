package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Dto.Vo.Common.AlarmMessage;
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
        List<AlarmMessage> messages = List.of(
                createAlarmMessage("ERROR", "test-source", "test message"));

        alarmService.processAlarm(messages);

        verify(alarmPublisher, times(1)).publish(messages);
        assertEquals("test message", messages.get(0).getMessage());
    }

    @Test
    void processAlarm_whenPublishThrowsException_shouldSetFailureMessage() {
        List<AlarmMessage> messages = List.of(
                createAlarmMessage("ERROR", "test-source", "original message"));
        doThrow(new RuntimeException("Kafka unavailable")).when(alarmPublisher).publish(messages);

        alarmService.processAlarm(messages);

        verify(alarmPublisher, times(1)).publish(messages);
        assertEquals("告警消息發送失敗：Kafka unavailable", messages.get(0).getMessage());
    }

    @Test
    void processAlarm_whenPublishThrowsRuntimeException_shouldHandleGracefully() {
        List<AlarmMessage> messages = List.of(
                createAlarmMessage("WARN", "api", "some warning"));
        doThrow(new RuntimeException("connection timeout")).when(alarmPublisher).publish(messages);

        assertDoesNotThrow(() -> alarmService.processAlarm(messages));

        assertTrue(messages.get(0).getMessage().contains("connection timeout"));
    }

    @Test
    void processAlarm_withMultipleMessages_shouldPublishAll() {
        List<AlarmMessage> messages = List.of(
                createAlarmMessage("ERROR", "src1", "msg1"),
                createAlarmMessage("WARN", "src2", "msg2"),
                createAlarmMessage("INFO", "src3", "msg3"));

        alarmService.processAlarm(messages);

        verify(alarmPublisher, times(1)).publish(messages);
    }

    @Test
    void processAlarm_whenPublishThrowsExceptionWithNullMessage_shouldHandleNullGetMessage() {
        List<AlarmMessage> messages = List.of(
                createAlarmMessage("ERROR", "test", "original"));
        RuntimeException ex = mock(RuntimeException.class);
        when(ex.getMessage()).thenReturn(null);
        doThrow(ex).when(alarmPublisher).publish(messages);

        alarmService.processAlarm(messages);

        assertEquals("告警消息發送失敗：null", messages.get(0).getMessage());
    }

    private AlarmMessage createAlarmMessage(String level, String source, String message) {
        AlarmMessage alarm = new AlarmMessage();
        alarm.setLevel(level);
        alarm.setSource(source);
        alarm.setMessage(message);
        return alarm;
    }
}
