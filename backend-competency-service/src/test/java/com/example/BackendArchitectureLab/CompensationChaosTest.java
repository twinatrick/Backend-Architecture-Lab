package com.example.BackendArchitectureLab;

import com.example.BackendArchitectureLab.Entity.CompensationOutboxEvent;
import com.example.BackendArchitectureLab.Feign.PermissionCheckFeignClient;
import com.example.BackendArchitectureLab.Feign.UserServiceFeignClient;
import com.example.BackendArchitectureLab.Repository.CompensationOutboxEventRepository;
import com.example.BackendArchitectureLab.Service.ICompensationPublisher;
import com.example.BackendArchitectureLab.Timer.CompensationOutboxWorker;
import com.example.BackendArchitectureLab.Vo.CacheStatsEvent;
import com.example.BackendArchitectureLab.Vo.CompensationOutboxDeliveryStatus;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ActiveProfiles("test")
@SpringBootTest(classes = CompetencyApplication.class)
class CompensationChaosTest {

    @Autowired
    private CompensationOutboxEventRepository outboxRepository;

    @Autowired
    private CompensationOutboxWorker compensationOutboxWorker;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ICompensationPublisher compensationPublisher;

    @MockBean
    private RedissonClient redissonClient;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockBean
    private KafkaTemplate<String, CacheStatsEvent> cacheStatsKafkaTemplate;

    @MockBean
    private PermissionCheckFeignClient permissionCheckFeignClient;

    @MockBean
    private UserServiceFeignClient userServiceFeignClient;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        when(compensationPublisher.publish(any(CompensationEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    @DisplayName("混沌場景 1 - Worker 暴斃與租約搶佔接管：過期租約由新 Worker 接管並遞增 Fencing Token 成功投遞")
    void testWorkerCrashAndLeasePreemption_ShouldReclaimAndDeliverSuccessfully() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();

        CompensationEvent payloadEvent = CompensationEvent.builder()
                .eventId(eventId)
                .eventVersion(1)
                .transactionId(txId)
                .serviceName("competency-service")
                .action(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND)
                .status(CompensationStatus.COMMITTED)
                .beforeState(Map.of("key", "val"))
                .timestamp(Instant.now())
                .build();

        // 模擬 Worker A 於執行中崩潰：狀態停留在 PROCESSING，但租約在 10 秒前已過期
        Date expiredLease = new Date(System.currentTimeMillis() - 10000L);
        CompensationOutboxEvent crashedWorkerEvent = new CompensationOutboxEvent();
        crashedWorkerEvent.setEventId(eventId);
        crashedWorkerEvent.setTransactionId(txId);
        crashedWorkerEvent.setAction(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND.name());
        crashedWorkerEvent.setStatus(CompensationStatus.COMMITTED);
        crashedWorkerEvent.setPayload(objectMapper.writeValueAsString(payloadEvent));
        crashedWorkerEvent.setDeliveryStatus(CompensationOutboxDeliveryStatus.PROCESSING);
        crashedWorkerEvent.setAttemptCount(1);
        crashedWorkerEvent.setFencingVersion(1L);
        crashedWorkerEvent.setOwnerId("worker-a-crashed");
        crashedWorkerEvent.setLeaseUntil(expiredLease);
        crashedWorkerEvent.setProcessingAt(new Date(System.currentTimeMillis() - 20000L));

        CompensationOutboxEvent saved = outboxRepository.saveAndFlush(crashedWorkerEvent);
        assertNotNull(saved.getId());

        // 執行 Worker 批次處理，模擬 Worker B 掃描並搶佔過期租約
        compensationOutboxWorker.flushPendingEvents();

        // 驗證 Worker B 成功接管並完成投遞
        CompensationOutboxEvent processed = outboxRepository.findById(saved.getId()).orElse(null);
        assertNotNull(processed);
        assertEquals(CompensationOutboxDeliveryStatus.SENT, processed.getDeliveryStatus(), "事件應被成功標記為 SENT");
        assertEquals(2, processed.getAttemptCount(), "attemptCount 應原子遞增為 2");
        assertEquals(2L, processed.getFencingVersion(), "fencingVersion 應由 1 遞增為 2");
        assertNull(processed.getLeaseUntil(), "SENT 狀態下租約時間應被清空");
        assertNotNull(processed.getSentAt(), "應記錄成功投遞時間戳");

        verify(compensationPublisher, atLeastOnce()).publish(any(CompensationEvent.class));
    }

    @Test
    @DisplayName("混沌場景 2 - 陳舊工作者隔離 (Stale Worker Fencing)：長 GC 甦醒之舊 Worker 因 Fencing Token 不合遭 DB 拒絕寫入")
    void testStaleWorkerLongGc_ShouldBeRejectedByFencingToken() {
        UUID eventId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();

        // 1. Worker A 曾領取此事件，持有 fencingVersion = 1, ownerId = worker-a-stale，租約已逾期
        CompensationOutboxEvent event = new CompensationOutboxEvent();
        event.setEventId(eventId);
        event.setTransactionId(txId);
        event.setAction(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND.name());
        event.setStatus(CompensationStatus.COMMITTED);
        event.setPayload("{}");
        event.setDeliveryStatus(CompensationOutboxDeliveryStatus.PROCESSING);
        event.setAttemptCount(1);
        event.setFencingVersion(1L);
        event.setOwnerId("worker-a-stale");
        event.setLeaseUntil(new Date(System.currentTimeMillis() - 5000L));

        CompensationOutboxEvent saved = outboxRepository.saveAndFlush(event);

        // 2. 由於租約已逾期，Worker B 透過 CAS claimEvent 接管
        String workerBOwnerId = "worker-b-active";
        Date now = new Date();
        int claimed = outboxRepository.claimEvent(
                saved.getId(),
                List.of(CompensationOutboxDeliveryStatus.PENDING,
                        CompensationOutboxDeliveryStatus.FAILED,
                        CompensationOutboxDeliveryStatus.PROCESSING),
                CompensationOutboxDeliveryStatus.PROCESSING,
                workerBOwnerId,
                now,
                new Date(now.getTime() + 60000L)
        );
        assertEquals(1, claimed, "Worker B 應成功搶佔接管租約");

        CompensationOutboxEvent claimedEvent = outboxRepository.findById(saved.getId()).orElseThrow();
        assertEquals(2L, claimedEvent.getFencingVersion(), "Fencing version 應已遞增至 2");
        assertEquals(workerBOwnerId, claimedEvent.getOwnerId());

        // 3. 模擬 Worker A 甦醒，試圖以過期 fencingVersion = 1 及舊 ownerId 更新 DB
        int staleSentAffected = outboxRepository.markSent(
                saved.getId(),
                "worker-a-stale",
                1L,
                CompensationOutboxDeliveryStatus.SENT,
                CompensationOutboxDeliveryStatus.PROCESSING,
                new Date()
        );
        assertEquals(0, staleSentAffected, "Stale Worker A 呼叫 markSent 必須影響 0 筆記錄 (遭 DB Fencing 攔截)");

        int staleFailedAffected = outboxRepository.markFailed(
                saved.getId(),
                "worker-a-stale",
                1L,
                CompensationOutboxDeliveryStatus.FAILED,
                CompensationOutboxDeliveryStatus.PROCESSING,
                "stale error",
                new Date()
        );
        assertEquals(0, staleFailedAffected, "Stale Worker A 呼叫 markFailed 必須影響 0 筆記錄");

        // 4. 正當的 Worker B 呼叫 markSent (持 version=2)，成功更新
        int validSentAffected = outboxRepository.markSent(
                saved.getId(),
                workerBOwnerId,
                2L,
                CompensationOutboxDeliveryStatus.SENT,
                CompensationOutboxDeliveryStatus.PROCESSING,
                new Date()
        );
        assertEquals(1, validSentAffected, "有效持有最新 Fencing Token 的 Worker B 應成功更新狀態");

        CompensationOutboxEvent finalEvent = outboxRepository.findById(saved.getId()).orElseThrow();
        assertEquals(CompensationOutboxDeliveryStatus.SENT, finalEvent.getDeliveryStatus());
    }
}
