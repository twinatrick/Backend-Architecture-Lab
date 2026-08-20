package com.example.BackendArchitectureLab.Timer;

import com.example.BackendArchitectureLab.Entity.CompensationOutboxEvent;
import com.example.BackendArchitectureLab.DataAccess.ICompensationOutboxEventDataAccess;
import com.example.BackendArchitectureLab.Service.ICompensationPublisher;
import com.example.BackendArchitectureLab.Vo.CompensationOutboxDeliveryStatus;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationAction;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompensationOutboxWorkerTest {

    @Mock
    private ICompensationOutboxEventDataAccess outboxRepository;

    @Mock
    private ICompensationPublisher compensationPublisher;

    private CompensationOutboxWorker compensationOutboxWorker;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private ExecutorService publisherPool;

    @BeforeEach
    void setUp() {
        publisherPool = Executors.newFixedThreadPool(2);
        compensationOutboxWorker = new CompensationOutboxWorker(
                outboxRepository,
                compensationPublisher,
                objectMapper,
                publisherPool
        );
        ReflectionTestUtils.setField(compensationOutboxWorker, "maxAttempts", 5);
        ReflectionTestUtils.setField(compensationOutboxWorker, "leaseSeconds", 300L);
        ReflectionTestUtils.setField(compensationOutboxWorker, "batchSize", 20);
        ReflectionTestUtils.setField(compensationOutboxWorker, "publishParallelism", 8);
        ReflectionTestUtils.setField(compensationOutboxWorker, "ackTimeoutSeconds", 10L);
        ReflectionTestUtils.setField(compensationOutboxWorker, "backoffSeconds", List.of(5L, 15L, 30L, 60L, 300L));
        compensationOutboxWorker.validateConfiguration();
    }

    @Test
    void flushPendingEvents_ShouldPublishAndMarkSentAfterAck() throws Exception {
        CompensationOutboxEvent outbox = newOutboxEvent(CompensationStatus.COMMITTED);
        CompensationOutboxEvent fresh = freshWithAttempt(outbox, 1);
        when(outboxRepository.findPendingDue(anyList(), anyString(), any(Pageable.class))).thenReturn(List.of(outbox));
        when(outboxRepository.claimEvent(any(UUID.class), anyList(), anyString(), anyString(), any(Date.class), any(Date.class))).thenReturn(1);
        when(outboxRepository.findById(outbox.getId())).thenReturn(Optional.of(fresh));
        when(compensationPublisher.publish(any(CompensationEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        compensationOutboxWorker.flushPendingEvents();

        ArgumentCaptor<CompensationEvent> eventCaptor = ArgumentCaptor.forClass(CompensationEvent.class);
        verify(compensationPublisher).publish(eventCaptor.capture());
        assertEquals(outbox.getEventId(), eventCaptor.getValue().getEventId());
        assertEquals(CompensationStatus.COMMITTED, eventCaptor.getValue().getStatus());

        verify(outboxRepository).markSent(eq(outbox.getId()),
                anyString(),
                eq(fresh.getFencingVersion()),
                eq(CompensationOutboxDeliveryStatus.SENT),
                eq(CompensationOutboxDeliveryStatus.PROCESSING),
                any(Date.class));
        verify(outboxRepository, never()).save(any(CompensationOutboxEvent.class));
    }

    @Test
    void flushPendingEvents_ShouldReclaimExpiredLeaseAndPublish() throws Exception {
        CompensationOutboxEvent outbox = newOutboxEvent(CompensationStatus.COMMITTED);
        outbox.setDeliveryStatus(CompensationOutboxDeliveryStatus.PROCESSING);
        outbox.setProcessingAt(new Date(System.currentTimeMillis() - 600_000L));
        outbox.setLeaseUntil(new Date(System.currentTimeMillis() - 60_000L));
        outbox.setAttemptCount(1);
        CompensationOutboxEvent fresh = freshWithAttempt(outbox, 2);
        when(outboxRepository.findPendingDue(anyList(), anyString(), any(Pageable.class))).thenReturn(List.of(outbox));
        when(outboxRepository.claimEvent(any(UUID.class), anyList(), anyString(), anyString(), any(Date.class), any(Date.class))).thenReturn(1);
        when(outboxRepository.findById(outbox.getId())).thenReturn(Optional.of(fresh));
        when(compensationPublisher.publish(any(CompensationEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        compensationOutboxWorker.flushPendingEvents();

        verify(compensationPublisher).publish(any(CompensationEvent.class));
        verify(outboxRepository).markSent(eq(outbox.getId()),
                anyString(),
                eq(fresh.getFencingVersion()),
                eq(CompensationOutboxDeliveryStatus.SENT),
                eq(CompensationOutboxDeliveryStatus.PROCESSING),
                any(Date.class));
    }

    @Test
    void flushPendingEvents_ShouldSkip_whenClaimReturnsZeroOrLeaseNotExpired() throws Exception {
        CompensationOutboxEvent outbox = newOutboxEvent(CompensationStatus.COMMITTED);
        outbox.setDeliveryStatus(CompensationOutboxDeliveryStatus.PROCESSING);
        outbox.setLeaseUntil(new Date(System.currentTimeMillis() + 60_000L));
        when(outboxRepository.findPendingDue(anyList(), anyString(), any(Pageable.class))).thenReturn(List.of(outbox));
        when(outboxRepository.claimEvent(any(UUID.class), anyList(), anyString(), anyString(), any(Date.class), any(Date.class))).thenReturn(0);

        compensationOutboxWorker.flushPendingEvents();

        verifyNoInteractions(compensationPublisher);
        verify(outboxRepository, never()).save(any(CompensationOutboxEvent.class));
        verify(outboxRepository, never()).markSent(any(UUID.class), anyString(), any(), anyString(), anyString(), any(Date.class));
    }

    @Test
    void flushPendingEvents_ShouldNotPublish_whenNoPendingEvents() {
        when(outboxRepository.findPendingDue(anyList(), anyString(), any(Pageable.class))).thenReturn(List.of());

        compensationOutboxWorker.flushPendingEvents();

        verifyNoInteractions(compensationPublisher);
        verify(outboxRepository, never()).save(any(CompensationOutboxEvent.class));
    }

    @Test
    void flushPendingEvents_ShouldRetryWithBackoff_whenPublishFails() throws Exception {
        CompensationOutboxEvent outbox = newOutboxEvent(CompensationStatus.COMMITTED);
        CompensationOutboxEvent fresh = freshWithAttempt(outbox, 1);
        when(outboxRepository.findPendingDue(anyList(), anyString(), any(Pageable.class))).thenReturn(List.of(outbox));
        when(outboxRepository.claimEvent(any(UUID.class), anyList(), anyString(), anyString(), any(Date.class), any(Date.class))).thenReturn(1);
        when(outboxRepository.findById(outbox.getId())).thenReturn(Optional.of(fresh));
        CompletableFuture<Void> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("broker unreachable"));
        when(compensationPublisher.publish(any(CompensationEvent.class))).thenReturn(failedFuture);

        compensationOutboxWorker.flushPendingEvents();

        ArgumentCaptor<Date> nextAttemptCaptor = ArgumentCaptor.forClass(Date.class);
        verify(outboxRepository).markFailed(eq(outbox.getId()),
                anyString(),
                eq(fresh.getFencingVersion()),
                eq(CompensationOutboxDeliveryStatus.FAILED),
                eq(CompensationOutboxDeliveryStatus.PROCESSING),
                contains("broker unreachable"),
                nextAttemptCaptor.capture());
        assertNotNull(nextAttemptCaptor.getValue());
        verify(outboxRepository, never()).save(any(CompensationOutboxEvent.class));
    }

    @Test
    void flushPendingEvents_ShouldMarkDead_whenMaxAttemptsReached() throws Exception {
        CompensationOutboxEvent outbox = newOutboxEvent(CompensationStatus.COMMITTED);
        outbox.setAttemptCount(4);
        CompensationOutboxEvent fresh = freshWithAttempt(outbox, 5);
        when(outboxRepository.findPendingDue(anyList(), anyString(), any(Pageable.class))).thenReturn(List.of(outbox));
        when(outboxRepository.claimEvent(any(UUID.class), anyList(), anyString(), anyString(), any(Date.class), any(Date.class))).thenReturn(1);
        when(outboxRepository.findById(outbox.getId())).thenReturn(Optional.of(fresh));
        CompletableFuture<Void> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("broker unreachable"));
        when(compensationPublisher.publish(any(CompensationEvent.class))).thenReturn(failedFuture);

        compensationOutboxWorker.flushPendingEvents();

        verify(outboxRepository).markDead(eq(outbox.getId()),
                anyString(),
                eq(fresh.getFencingVersion()),
                eq(CompensationOutboxDeliveryStatus.DEAD),
                eq(CompensationOutboxDeliveryStatus.PROCESSING),
                contains("broker unreachable"));
        verify(outboxRepository, never()).save(any(CompensationOutboxEvent.class));
    }

    @Test
    void flushPendingEvents_ShouldCountAttemptAndRetain_whenPayloadCorrupted() {
        CompensationOutboxEvent outbox = new CompensationOutboxEvent();
        outbox.setId(UUID.randomUUID());
        outbox.setEventId(UUID.randomUUID());
        outbox.setTransactionId(UUID.randomUUID());
        outbox.setAction(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND.name());
        outbox.setStatus(CompensationStatus.COMMITTED);
        outbox.setPayload("not-valid-json{");
        CompensationOutboxEvent fresh = freshWithAttempt(outbox, 1);
        when(outboxRepository.findPendingDue(anyList(), anyString(), any(Pageable.class))).thenReturn(List.of(outbox));
        when(outboxRepository.claimEvent(any(UUID.class), anyList(), anyString(), anyString(), any(Date.class), any(Date.class))).thenReturn(1);
        when(outboxRepository.findById(outbox.getId())).thenReturn(Optional.of(fresh));

        compensationOutboxWorker.flushPendingEvents();

        verifyNoInteractions(compensationPublisher);
        verify(outboxRepository).markFailed(eq(outbox.getId()),
                anyString(),
                eq(fresh.getFencingVersion()),
                eq(CompensationOutboxDeliveryStatus.FAILED),
                eq(CompensationOutboxDeliveryStatus.PROCESSING),
                contains("JSON"),
                any(Date.class));
    }

    @Test
    void validateConfiguration_shouldThrow_whenLeaseTooShortForBatchWindow() {
        ReflectionTestUtils.setField(compensationOutboxWorker, "leaseSeconds", 5L);
        ReflectionTestUtils.setField(compensationOutboxWorker, "batchSize", 100);
        ReflectionTestUtils.setField(compensationOutboxWorker, "ackTimeoutSeconds", 10L);
        ReflectionTestUtils.setField(compensationOutboxWorker, "publishParallelism", 1);

        assertThrows(IllegalStateException.class, compensationOutboxWorker::validateConfiguration);
    }

    @Test
    void flushPendingEvents_ShouldRespectSemaphorePermitsAndRelease_whenConcurrentTasksRun() throws Exception {
        ReflectionTestUtils.setField(compensationOutboxWorker, "publishParallelism", 2);
        compensationOutboxWorker.validateConfiguration();
        CompensationOutboxEvent outbox1 = newOutboxEvent(CompensationStatus.COMMITTED);
        CompensationOutboxEvent fresh1 = freshWithAttempt(outbox1, 1);
        CompensationOutboxEvent outbox2 = newOutboxEvent(CompensationStatus.COMMITTED);
        CompensationOutboxEvent fresh2 = freshWithAttempt(outbox2, 1);

        when(outboxRepository.findPendingDue(anyList(), anyString(), any(Pageable.class))).thenReturn(List.of(outbox1, outbox2));
        when(outboxRepository.claimEvent(any(UUID.class), anyList(), anyString(), anyString(), any(Date.class), any(Date.class))).thenReturn(1);
        when(outboxRepository.findById(outbox1.getId())).thenReturn(Optional.of(fresh1));
        when(outboxRepository.findById(outbox2.getId())).thenReturn(Optional.of(fresh2));
        when(compensationPublisher.publish(any(CompensationEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        compensationOutboxWorker.flushPendingEvents();

        Semaphore semaphore = (Semaphore) ReflectionTestUtils.getField(compensationOutboxWorker, "publishSemaphore");
        assertNotNull(semaphore);
        assertEquals(2, semaphore.availablePermits());
    }

    @Test
    void flushPendingEvents_ShouldReleaseSemaphore_evenWhenExceptionThrownInClaimOrPublish() throws Exception {
        ReflectionTestUtils.setField(compensationOutboxWorker, "publishParallelism", 1);
        compensationOutboxWorker.validateConfiguration();
        CompensationOutboxEvent outbox = newOutboxEvent(CompensationStatus.COMMITTED);
        when(outboxRepository.findPendingDue(anyList(), anyString(), any(Pageable.class))).thenReturn(List.of(outbox));
        when(outboxRepository.claimEvent(any(UUID.class), anyList(), anyString(), anyString(), any(Date.class), any(Date.class)))
                .thenThrow(new RuntimeException("database claim connection lost"));

        compensationOutboxWorker.flushPendingEvents();

        Semaphore semaphore = (Semaphore) ReflectionTestUtils.getField(compensationOutboxWorker, "publishSemaphore");
        assertNotNull(semaphore);
        assertEquals(1, semaphore.availablePermits());
    }

    @Test
    void flushPendingEvents_ShouldThrottleConcurrency_whenTasksExceedParallelism() throws Exception {
        ReflectionTestUtils.setField(compensationOutboxWorker, "publishParallelism", 2);
        compensationOutboxWorker.validateConfiguration();
        CompensationOutboxEvent outbox1 = newOutboxEvent(CompensationStatus.COMMITTED);
        CompensationOutboxEvent fresh1 = freshWithAttempt(outbox1, 1);
        CompensationOutboxEvent outbox2 = newOutboxEvent(CompensationStatus.COMMITTED);
        CompensationOutboxEvent fresh2 = freshWithAttempt(outbox2, 1);
        CompensationOutboxEvent outbox3 = newOutboxEvent(CompensationStatus.COMMITTED);
        CompensationOutboxEvent fresh3 = freshWithAttempt(outbox3, 1);

        when(outboxRepository.findPendingDue(anyList(), anyString(), any(Pageable.class)))
                .thenReturn(List.of(outbox1, outbox2, outbox3));
        when(outboxRepository.claimEvent(any(UUID.class), anyList(), anyString(), anyString(), any(Date.class), any(Date.class))).thenReturn(1);
        when(outboxRepository.findById(outbox1.getId())).thenReturn(Optional.of(fresh1));
        when(outboxRepository.findById(outbox2.getId())).thenReturn(Optional.of(fresh2));
        when(outboxRepository.findById(outbox3.getId())).thenReturn(Optional.of(fresh3));

        AtomicInteger activeConcurrent = new AtomicInteger(0);
        AtomicInteger maxConcurrentObserved = new AtomicInteger(0);
        CountDownLatch firstWaveLatch = new CountDownLatch(2);
        CountDownLatch releaseLatch = new CountDownLatch(1);

        when(compensationPublisher.publish(any(CompensationEvent.class))).thenAnswer(invocation -> {
            int current = activeConcurrent.incrementAndGet();
            maxConcurrentObserved.accumulateAndGet(current, Math::max);
            firstWaveLatch.countDown();
            try {
                releaseLatch.await(2, TimeUnit.SECONDS);
            } finally {
                activeConcurrent.decrementAndGet();
            }
            return CompletableFuture.completedFuture(null);
        });

        CompletableFuture.runAsync(() -> {
            try {
                if (firstWaveLatch.await(2, TimeUnit.SECONDS)) {
                    releaseLatch.countDown();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });

        compensationOutboxWorker.flushPendingEvents();

        assertEquals(2, maxConcurrentObserved.get());
        Semaphore semaphore = (Semaphore) ReflectionTestUtils.getField(compensationOutboxWorker, "publishSemaphore");
        assertNotNull(semaphore);
        assertEquals(2, semaphore.availablePermits());
    }

    @Test
    void flushPendingEvents_ShouldReturnGracefully_whenFreshEventNotFoundAfterClaim() throws Exception {
        ReflectionTestUtils.setField(compensationOutboxWorker, "publishParallelism", 1);
        compensationOutboxWorker.validateConfiguration();
        CompensationOutboxEvent outbox = newOutboxEvent(CompensationStatus.COMMITTED);
        outbox.setAttemptCount(1);
        when(outboxRepository.findPendingDue(anyList(), anyString(), any(Pageable.class))).thenReturn(List.of(outbox));
        when(outboxRepository.claimEvent(any(UUID.class), anyList(), anyString(), anyString(), any(Date.class), any(Date.class))).thenReturn(1);
        when(outboxRepository.findById(outbox.getId())).thenReturn(Optional.empty());

        compensationOutboxWorker.flushPendingEvents();

        verify(compensationPublisher, never()).publish(any());
        verify(outboxRepository).markFailedByOwner(eq(outbox.getId()),
                anyString(),
                eq(CompensationOutboxDeliveryStatus.FAILED),
                eq(CompensationOutboxDeliveryStatus.PROCESSING),
                contains("not found"),
                any(Date.class));
        Semaphore semaphore = (Semaphore) ReflectionTestUtils.getField(compensationOutboxWorker, "publishSemaphore");
        assertNotNull(semaphore);
        assertEquals(1, semaphore.availablePermits());
    }

    @Test
    void flushPendingEvents_ShouldMarkDeadByOwner_whenFreshEventNotFoundAndMaxAttemptsReached() throws Exception {
        ReflectionTestUtils.setField(compensationOutboxWorker, "publishParallelism", 1);
        compensationOutboxWorker.validateConfiguration();
        CompensationOutboxEvent outbox = newOutboxEvent(CompensationStatus.COMMITTED);
        outbox.setAttemptCount(4); // +1 in worker when fresh entity missing -> 5 >= maxAttempts (5)
        when(outboxRepository.findPendingDue(anyList(), anyString(), any(Pageable.class))).thenReturn(List.of(outbox));
        when(outboxRepository.claimEvent(any(UUID.class), anyList(), anyString(), anyString(), any(Date.class), any(Date.class))).thenReturn(1);
        when(outboxRepository.findById(outbox.getId())).thenReturn(Optional.empty());

        compensationOutboxWorker.flushPendingEvents();

        verify(compensationPublisher, never()).publish(any());
        verify(outboxRepository).markDeadByOwner(eq(outbox.getId()),
                anyString(),
                eq(CompensationOutboxDeliveryStatus.DEAD),
                eq(CompensationOutboxDeliveryStatus.PROCESSING),
                contains("not found"));
        Semaphore semaphore = (Semaphore) ReflectionTestUtils.getField(compensationOutboxWorker, "publishSemaphore");
        assertNotNull(semaphore);
        assertEquals(1, semaphore.availablePermits());
    }

    @Test
    void flushPendingEvents_ShouldSkip_whenAlreadyFlushing() throws Exception {
        AtomicBoolean isFlushing = (AtomicBoolean) ReflectionTestUtils.getField(compensationOutboxWorker, "isFlushing");
        assertNotNull(isFlushing);
        isFlushing.set(true);

        try {
            compensationOutboxWorker.flushPendingEvents();
            verifyNoInteractions(outboxRepository);
            verifyNoInteractions(compensationPublisher);
        } finally {
            isFlushing.set(false);
        }
    }

    @Test
    void flushPendingEvents_ShouldCancelRunningTasks_whenBatchWaitInterrupted() throws Exception {
        ReflectionTestUtils.setField(compensationOutboxWorker, "publishParallelism", 1);
        ReflectionTestUtils.setField(compensationOutboxWorker, "ackTimeoutSeconds", 5L);
        ReflectionTestUtils.setField(compensationOutboxWorker, "batchSize", 1);
        ReflectionTestUtils.setField(compensationOutboxWorker, "leaseSeconds", 300L);
        compensationOutboxWorker.validateConfiguration();

        CompensationOutboxEvent outbox = newOutboxEvent(CompensationStatus.COMMITTED);
        CompensationOutboxEvent fresh = freshWithAttempt(outbox, 1);

        when(outboxRepository.findPendingDue(anyList(), anyString(), any(Pageable.class))).thenReturn(List.of(outbox));
        when(outboxRepository.claimEvent(any(UUID.class), anyList(), anyString(), anyString(), any(Date.class), any(Date.class))).thenReturn(1);
        when(outboxRepository.findById(outbox.getId())).thenReturn(Optional.of(fresh));

        CountDownLatch publishStartedLatch = new CountDownLatch(1);
        when(compensationPublisher.publish(any(CompensationEvent.class))).thenAnswer(invocation -> {
            publishStartedLatch.countDown();
            CompletableFuture<Void> future = new CompletableFuture<>();
            Executors.newSingleThreadScheduledExecutor().schedule(() -> {
                future.complete(null);
            }, 500, TimeUnit.MILLISECONDS);
            return future;
        });

        // 模擬呼叫執行緒在等待批次 Future 時被中斷（例如排程終止或容器信號）
        Thread mainThread = Thread.currentThread();
        Executors.newSingleThreadScheduledExecutor().schedule(() -> {
            try {
                if (publishStartedLatch.await(1, TimeUnit.SECONDS)) {
                    mainThread.interrupt();
                }
            } catch (InterruptedException ignored) {
            }
        }, 30, TimeUnit.MILLISECONDS);

        compensationOutboxWorker.flushPendingEvents();

        // 呼叫執行緒之中斷旗標應已恢復
        assertEquals(true, Thread.interrupted());
    }

    @Test
    void flushPendingEvents_ShouldMarkFailed_whenDeliveryInterrupted() throws Exception {
        ReflectionTestUtils.setField(compensationOutboxWorker, "publishParallelism", 1);
        compensationOutboxWorker.validateConfiguration();
        CompensationOutboxEvent outbox = newOutboxEvent(CompensationStatus.COMMITTED);
        CompensationOutboxEvent fresh = freshWithAttempt(outbox, 1);

        when(outboxRepository.findPendingDue(anyList(), anyString(), any(Pageable.class))).thenReturn(List.of(outbox));
        when(outboxRepository.claimEvent(any(UUID.class), anyList(), anyString(), anyString(), any(Date.class), any(Date.class))).thenReturn(1);
        when(outboxRepository.findById(outbox.getId())).thenReturn(Optional.of(fresh));

        CompletableFuture<Void> interruptedFuture = new CompletableFuture<>() {
            @Override
            public Void get(long timeout, TimeUnit unit) throws InterruptedException {
                throw new InterruptedException("Simulated task interruption during wait");
            }
        };
        when(compensationPublisher.publish(any(CompensationEvent.class))).thenReturn(interruptedFuture);

        compensationOutboxWorker.flushPendingEvents();

        ArgumentCaptor<Date> nextAttemptCaptor = ArgumentCaptor.forClass(Date.class);
        verify(outboxRepository).markFailed(eq(outbox.getId()),
                anyString(),
                eq(fresh.getFencingVersion()),
                eq(CompensationOutboxDeliveryStatus.FAILED),
                eq(CompensationOutboxDeliveryStatus.PROCESSING),
                contains("Simulated task interruption"),
                nextAttemptCaptor.capture());
        assertNotNull(nextAttemptCaptor.getValue());

        Semaphore semaphore = (Semaphore) ReflectionTestUtils.getField(compensationOutboxWorker, "publishSemaphore");
        assertNotNull(semaphore);
        assertEquals(1, semaphore.availablePermits());
    }

    @Test
    void flushPendingEvents_ShouldHandleAckTimeoutAndReleaseSemaphore() throws Exception {
        ReflectionTestUtils.setField(compensationOutboxWorker, "publishParallelism", 1);
        ReflectionTestUtils.setField(compensationOutboxWorker, "ackTimeoutSeconds", 1L);
        compensationOutboxWorker.validateConfiguration();
        CompensationOutboxEvent outbox = newOutboxEvent(CompensationStatus.COMMITTED);
        CompensationOutboxEvent fresh = freshWithAttempt(outbox, 1);

        when(outboxRepository.findPendingDue(anyList(), anyString(), any(Pageable.class))).thenReturn(List.of(outbox));
        when(outboxRepository.claimEvent(any(UUID.class), anyList(), anyString(), anyString(), any(Date.class), any(Date.class))).thenReturn(1);
        when(outboxRepository.findById(outbox.getId())).thenReturn(Optional.of(fresh));

        CompletableFuture<Void> neverEndingFuture = new CompletableFuture<>();
        when(compensationPublisher.publish(any(CompensationEvent.class))).thenReturn(neverEndingFuture);

        compensationOutboxWorker.flushPendingEvents();

        ArgumentCaptor<Date> nextAttemptCaptor = ArgumentCaptor.forClass(Date.class);
        verify(outboxRepository).markFailed(eq(outbox.getId()),
                anyString(),
                eq(fresh.getFencingVersion()),
                eq(CompensationOutboxDeliveryStatus.FAILED),
                eq(CompensationOutboxDeliveryStatus.PROCESSING),
                contains("TimeoutException"),
                nextAttemptCaptor.capture());
        assertNotNull(nextAttemptCaptor.getValue());

        Semaphore semaphore = (Semaphore) ReflectionTestUtils.getField(compensationOutboxWorker, "publishSemaphore");
        assertNotNull(semaphore);
        assertEquals(1, semaphore.availablePermits());
    }

    @Test
    void flushPendingEvents_ShouldHandleStaleFencingVersionWhenMarkSentReturnsZero() throws Exception {
        ReflectionTestUtils.setField(compensationOutboxWorker, "publishParallelism", 1);
        compensationOutboxWorker.validateConfiguration();
        CompensationOutboxEvent outbox = newOutboxEvent(CompensationStatus.COMMITTED);
        CompensationOutboxEvent fresh = freshWithAttempt(outbox, 1);

        when(outboxRepository.findPendingDue(anyList(), anyString(), any(Pageable.class))).thenReturn(List.of(outbox));
        when(outboxRepository.claimEvent(any(UUID.class), anyList(), anyString(), anyString(), any(Date.class), any(Date.class))).thenReturn(1);
        when(outboxRepository.findById(outbox.getId())).thenReturn(Optional.of(fresh));
        when(compensationPublisher.publish(any(CompensationEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(outboxRepository.markSent(any(UUID.class), anyString(), any(), anyString(), anyString(), any(Date.class)))
                .thenReturn(0);

        compensationOutboxWorker.flushPendingEvents();

        verify(outboxRepository).markSent(eq(outbox.getId()),
                anyString(),
                eq(fresh.getFencingVersion()),
                eq(CompensationOutboxDeliveryStatus.SENT),
                eq(CompensationOutboxDeliveryStatus.PROCESSING),
                any(Date.class));

        Semaphore semaphore = (Semaphore) ReflectionTestUtils.getField(compensationOutboxWorker, "publishSemaphore");
        assertNotNull(semaphore);
        assertEquals(1, semaphore.availablePermits());
    }

    private CompensationOutboxEvent freshWithAttempt(CompensationOutboxEvent outbox, int attemptCount) {
        CompensationOutboxEvent fresh = new CompensationOutboxEvent();
        fresh.setId(outbox.getId());
        fresh.setEventId(outbox.getEventId());
        fresh.setTransactionId(outbox.getTransactionId());
        fresh.setAction(outbox.getAction());
        fresh.setStatus(outbox.getStatus());
        fresh.setPayload(outbox.getPayload());
        fresh.setDeliveryStatus(CompensationOutboxDeliveryStatus.PROCESSING);
        fresh.setAttemptCount(attemptCount);
        fresh.setFencingVersion(1L);
        return fresh;
    }

    private CompensationOutboxEvent newOutboxEvent(String status) throws Exception {
        CompensationOutboxEvent outbox = new CompensationOutboxEvent();
        outbox.setId(UUID.randomUUID());
        UUID eventId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        outbox.setEventId(eventId);
        outbox.setTransactionId(transactionId);
        outbox.setAction(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND.name());
        outbox.setStatus(status);
        CompensationEvent event = CompensationEvent.builder()
                .eventId(eventId)
                .eventVersion(1)
                .transactionId(transactionId)
                .serviceName("competency-service")
                .action(CompensationAction.PROJECT_MEMBER_SKILLS_REBIND)
                .status(status)
                .beforeState(Map.of("projectId", "p1"))
                .timestamp(Instant.now())
                .build();
        outbox.setPayload(objectMapper.writeValueAsString(event));
        return outbox;
    }
}
