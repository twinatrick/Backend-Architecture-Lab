package com.example.BackendArchitectureLab;

import com.example.BackendArchitectureLab.Entity.CompensationEventLog;
import com.example.BackendArchitectureLab.Entity.CompensationOutboxEvent;
import com.example.BackendArchitectureLab.Feign.PermissionCheckFeignClient;
import com.example.BackendArchitectureLab.Feign.UserServiceFeignClient;
import com.example.BackendArchitectureLab.Repository.CompensationEventLogRepository;
import com.example.BackendArchitectureLab.Repository.CompensationOutboxEventRepository;
import com.example.BackendArchitectureLab.Service.ICompensationOutboxService;
import com.example.BackendArchitectureLab.Service.ICompensationRestoreService;
import com.example.BackendArchitectureLab.TestSupport.BaseTestcontainersIntegrationTest;
import com.example.BackendArchitectureLab.TestSupport.SharedContainers;
import com.example.BackendArchitectureLab.Timer.CompensationOutboxWorker;
import com.example.BackendArchitectureLab.Vo.CompensationOutboxDeliveryStatus;
import com.example.BackendArchitectureLab.Vo.CompensationRestoreResultVo;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEventLogStatus;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationStatus;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ActiveProfiles("test")
@SpringBootTest(classes = CompetencyApplication.class)
public class CompensationSagaEndToEndIT extends BaseTestcontainersIntegrationTest {

    @Autowired
    private ICompensationOutboxService compensationOutboxService;

    @Autowired
    private CompensationOutboxWorker compensationOutboxWorker;

    @Autowired
    private CompensationOutboxEventRepository outboxEventRepository;

    @Autowired
    private CompensationEventLogRepository eventLogRepository;

    @Autowired
    private KafkaTemplate<String, CompensationEvent> compensationKafkaTemplate;

    @MockBean
    private ICompensationRestoreService compensationRestoreService;

    @MockBean
    private PermissionCheckFeignClient permissionCheckFeignClient;

    @MockBean
    private UserServiceFeignClient userServiceFeignClient;

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> SharedContainers.getPostgresUrlForDatabase("competency_service"));
        registry.add("spring.datasource.username", SharedContainers::getPostgresUsername);
        registry.add("spring.datasource.password", SharedContainers::getPostgresPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.kafka.consumer.group-id", () -> "competency-saga-it-" + UUID.randomUUID());
    }

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        eventLogRepository.deleteAll();
    }

    @Test
    @DisplayName("SAGA 補償端到端流程：Outbox 排入 -> Worker 發佈至 Kafka -> Consumer 接收並執行補償閉環")
    void testOutboxToKafkaToConsumerEndToEndFlow() {
        UUID transactionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();

        Map<String, Object> state = new HashMap<>();
        state.put("projectId", projectId.toString());
        state.put("expectedVersion", 1L);
        state.put("bindings", List.of(Map.of(
                "userId", userId.toString(),
                "skillId", skillId.toString(),
                "levelId", levelId.toString()
        )));

        doReturn(new CompensationRestoreResultVo(true, "SUCCESS", UUID.randomUUID(), UUID.randomUUID()))
                .when(compensationRestoreService)
                .restoreMemberSkills(any(), any(), anyLong(), anyString(), anyLong(), any());

        // 1. 業務失敗觸發 Outbox 入庫
        compensationOutboxService.enqueueFailureAndCompensationRequired(
                transactionId,
                CompensationAction.PROJECT_MEMBER_SKILLS_REBIND,
                state,
                "E2E Test Simulated Error"
        );

        List<CompensationOutboxEvent> savedEvents = outboxEventRepository.findByTransactionId(transactionId);
        assertEquals(2, savedEvents.size(), "應同時寫入 FAILED 與 COMPENSATION_REQUIRED 兩筆 Outbox 事件");

        // 2. 觸發 Worker 批次處理發佈
        compensationOutboxWorker.flushPendingEvents();

        // 3. 驗證 Outbox 事件在真實 PostgreSQL 中成功轉換為 SENT 狀態
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    List<CompensationOutboxEvent> currentEvents = outboxEventRepository.findByTransactionId(transactionId);
                    long sentCount = currentEvents.stream()
                            .filter(e -> e.getDeliveryStatus().equals(CompensationOutboxDeliveryStatus.SENT))
                            .count();
                    assertEquals(2, sentCount, "所有 Outbox 事件應成功發送並更新為 SENT");
                });

        // 4. 驗證 Kafka Consumer 收到 COMPENSATION_REQUIRED 事件並完成補償處理寫入 Log
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    List<CompensationEventLog> logs = eventLogRepository.findAll();
                    boolean processed = logs.stream().anyMatch(log ->
                            transactionId.equals(log.getTransactionId()) &&
                                    CompensationEventLogStatus.PROCESSED.equals(log.getStatus())
                    );
                    assertEquals(true, processed, "Kafka 消費端應完成補償並記錄 PROCESSED 狀態");
                });

        verify(compensationRestoreService, times(1))
                .restoreMemberSkills(any(), any(), anyLong(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("等冪性去重驗證：重複投遞相同 eventId 之事件不應重複觸發補償邏輯")
    void testIdempotentEventProcessing() {
        UUID transactionId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();

        Map<String, Object> state = new HashMap<>();
        state.put("projectId", projectId.toString());
        state.put("expectedVersion", 1L);
        state.put("bindings", List.of(Map.of(
                "userId", userId.toString(),
                "skillId", skillId.toString(),
                "levelId", levelId.toString()
        )));

        CompensationEvent event = CompensationEvent.builder()
                .eventId(eventId)
                .eventVersion(1)
                .transactionId(transactionId)
                .serviceName("competency-service")
                .action(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND)
                .status(CompensationStatus.COMPENSATION_REQUIRED)
                .beforeState(state)
                .errorMessage("Duplicate test")
                .timestamp(Instant.now())
                .build();

        doReturn(new CompensationRestoreResultVo(true, "SUCCESS", UUID.randomUUID(), eventId))
                .when(compensationRestoreService)
                .restoreMemberSkills(any(), any(), anyLong(), anyString(), anyLong(), any());

        // 第一次發送
        compensationKafkaTemplate.send("transaction-compensation", transactionId.toString(), event);

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    CompensationEventLog log = eventLogRepository.findByEventId(eventId).orElse(null);
                    assertNotNull(log);
                    assertEquals(CompensationEventLogStatus.PROCESSED, log.getStatus());
                });

        // 第二次發送相同 eventId
        compensationKafkaTemplate.send("transaction-compensation", transactionId.toString(), event);

        // 等待緩衝時間，確認未被重複執行
        try {
            Thread.sleep(1500);
        } catch (InterruptedException ignored) {
        }

        verify(compensationRestoreService, times(1))
                .restoreMemberSkills(any(), any(), anyLong(), anyString(), anyLong(), any());
    }
}
