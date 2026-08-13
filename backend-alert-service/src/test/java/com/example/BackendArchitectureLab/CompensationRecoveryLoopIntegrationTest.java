package com.example.BackendArchitectureLab;

import com.example.BackendArchitectureLab.Entity.CompensationEventLog;
import com.example.BackendArchitectureLab.Feign.CompetencyServiceFeignClient;
import com.example.BackendArchitectureLab.Repository.CompensationEventLogRepository;
import com.example.BackendArchitectureLab.Service.CompensationEventProcessor;
import com.example.BackendArchitectureLab.Service.Strategy.ProjectMemberSkillsRebindCompensationStrategy;
import com.example.BackendArchitectureLab.Timer.CompensationLeaseReclaimer;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEventLogStatus;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 補償 FAILED 事件恢復迴圈整合測試（alert-service 首個 @SpringBootTest）：
 * <p>
 * 驗證 MR #44 Issue 1 的完整閉環：PROCESSING 租約過期 → reclaimer 回收 → transient failure
 * → 標記 FAILED + nextAttemptAt → 排程再次掃到 → retryClaim CAS 重新領取 → 補償成功 → PROCESSED。
 * 同時驗證 Issue 2 的 Feign required 注入（strategy 依賴 CompetencyServiceFeignClient bean）。
 */
@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = WebEnvironment.NONE,
        classes = CompensationRecoveryLoopIntegrationTest.CompensationTestApp.class,
        properties = {
                "app.init.enabled=false",
                "spring.autoconfigure.exclude="
                        + "org.redisson.spring.starter.RedissonAutoConfigurationV2,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.kafka.autoconfigure.KafkaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
                "spring.cloud.service-registry.auto-registration.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false"
        })
class CompensationRecoveryLoopIntegrationTest {

    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = CompensationEventLog.class)
    @EnableJpaRepositories(basePackageClasses = CompensationEventLogRepository.class)
    @Import({CompensationEventProcessor.class,
            CompensationLeaseReclaimer.class,
            ProjectMemberSkillsRebindCompensationStrategy.class})
    static class CompensationTestApp {
    }

    @Autowired
    private CompensationEventLogRepository eventLogRepository;

    @Autowired
    private CompensationLeaseReclaimer compensationLeaseReclaimer;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CompetencyServiceFeignClient competencyServiceFeignClient;

    @Test
    void failedEvent_shouldBeReclaimedAndRetriedToCompletion() throws Exception {
        CompensationEventLog processing = seedExpiredProcessingEvent();

        // 第一次回收：租約過期 PROCESSING 被接手，補償遭遇 transient failure → FAILED + nextAttemptAt
        doThrow(new RuntimeException("competency-service temporarily unreachable"))
                .doNothing()
                .when(competencyServiceFeignClient)
                .restoreProjectMemberSkills(any(), anyString(), any(), anyString(), anyLong(), any());

        compensationLeaseReclaimer.reclaimExpiredLeases();

        CompensationEventLog failed = eventLogRepository.findByEventId(processing.getEventId()).orElseThrow();
        assertEquals(CompensationEventLogStatus.FAILED, failed.getStatus());
        assertNotNull(failed.getNextAttemptAt(), "FAILED 事件應寫入 nextAttemptAt 供排程重新領取");
        assertEquals(2, failed.getAttemptCount(), "租約回收時嘗試次數應遞增");

        // 模擬退避時間流逝：將 nextAttemptAt 回推到過去，使事件達下次重試資格
        failed.setNextAttemptAt(new Date(System.currentTimeMillis() - 1_000L));
        eventLogRepository.save(failed);

        // 第二次回收：FAILED 且已達 nextAttemptAt → retryClaim CAS 重新領取 → 補償成功 → PROCESSED
        compensationLeaseReclaimer.reclaimExpiredLeases();

        CompensationEventLog processed = eventLogRepository.findByEventId(processing.getEventId()).orElseThrow();
        assertEquals(CompensationEventLogStatus.PROCESSED, processed.getStatus());
        assertNotNull(processed.getProcessedAt());
        assertEquals(3, processed.getAttemptCount(), "retryClaim 應再次遞增嘗試次數");
        verify(competencyServiceFeignClient, times(2))
                .restoreProjectMemberSkills(any(), anyString(), any(), anyString(), anyLong(), any());
    }

    private CompensationEventLog seedExpiredProcessingEvent() throws Exception {
        CompensationEvent event = CompensationEvent.builder()
                .eventId(UUID.randomUUID())
                .eventVersion(1)
                .transactionId(UUID.randomUUID())
                .serviceName("competency-service")
                .action(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND)
                .status(CompensationStatus.COMPENSATION_REQUIRED)
                .beforeState(Map.of(
                        "projectId", UUID.randomUUID().toString(),
                        "expectedVersion", 1L,
                        "bindings", List.of(Map.of("userId", UUID.randomUUID().toString()))))
                .timestamp(Instant.now())
                .build();

        CompensationEventLog log = new CompensationEventLog();
        log.setEventId(event.getEventId());
        log.setTransactionId(event.getTransactionId());
        log.setStatus(CompensationEventLogStatus.PROCESSING);
        log.setAttemptCount(1);
        log.setOwnerId("owner-" + UUID.randomUUID());
        log.setFencingVersion(1L);
        log.setReceivedAt(new Date(System.currentTimeMillis() - 600_000L));
        log.setProcessingAt(new Date(System.currentTimeMillis() - 600_000L));
        log.setLeaseUntil(new Date(System.currentTimeMillis() - 60_000L));
        log.setPayload(objectMapper.writeValueAsString(event));
        return eventLogRepository.saveAndFlush(log);
    }
}