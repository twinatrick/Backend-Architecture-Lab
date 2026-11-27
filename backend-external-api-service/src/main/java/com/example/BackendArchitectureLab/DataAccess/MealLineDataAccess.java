package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Entity.MealDraft;
import com.example.BackendArchitectureLab.Entity.MealLineBinding;
import com.example.BackendArchitectureLab.Entity.MealLineBindingCode;
import com.example.BackendArchitectureLab.Entity.MealLineEventDedup;
import com.example.BackendArchitectureLab.Entity.MealLineNotificationOutbox;
import com.example.BackendArchitectureLab.Repository.MealDraftRepository;
import com.example.BackendArchitectureLab.Repository.MealLineBindingCodeRepository;
import com.example.BackendArchitectureLab.Repository.MealLineBindingRepository;
import com.example.BackendArchitectureLab.Repository.MealLineEventDedupRepository;
import com.example.BackendArchitectureLab.Repository.MealLineNotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MealLineDataAccess {
    private final MealLineBindingCodeRepository bindingCodeRepository;
    private final MealLineBindingRepository bindingRepository;
    private final MealDraftRepository draftRepository;
    private final MealLineEventDedupRepository eventDedupRepository;
    private final MealLineNotificationOutboxRepository outboxRepository;

    public MealLineBindingCode saveBindingCode(MealLineBindingCode code) {
        return bindingCodeRepository.saveAndFlush(code);
    }

    public Optional<MealLineBindingCode> findUnconsumedCode(String bindingCode) {
        return bindingCodeRepository.findByBindingCodeAndConsumedFalse(bindingCode);
    }

    public Optional<MealLineBinding> findBindingByUser(UUID userId) {
        return bindingRepository.findByOwnerUserId(userId);
    }

    public Optional<MealLineBinding> findBindingByLineUser(String lineUserId) {
        return bindingRepository.findByLineUserId(lineUserId);
    }

    public MealLineBinding saveBinding(MealLineBinding binding) {
        return bindingRepository.saveAndFlush(binding);
    }

    public void deleteBindingByUser(UUID userId) {
        bindingRepository.deleteByOwnerUserId(userId);
    }

    public void deleteBinding(MealLineBinding binding) {
        bindingRepository.delete(binding);
    }

    public Optional<MealDraft> findActiveDraft(UUID userId) {
        String status = "ACTIVE";
        return draftRepository.findFirstByOwnerUserIdAndStatusOrderByUpdatedAtDesc(userId, status);
    }

    public Optional<MealDraft> findDraftById(UUID draftId) {
        return draftRepository.findById(draftId);
    }

    public long countActiveDraftsForReceipt(UUID userId, UUID receiptId, Date now) {
        String status = "ACTIVE";
        return draftRepository.countByOwnerUserIdAndReceiptIdAndStatusAndExpiresAtGreaterThan(
                userId, receiptId, status, now);
    }

    public List<MealDraft> findExpiredActiveDrafts(Date now) {
        String status = "ACTIVE";
        return draftRepository.findTop20ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(status, now);
    }

    public MealDraft saveDraft(MealDraft draft) {
        return draftRepository.saveAndFlush(draft);
    }

    public boolean existsEventDedup(String eventKey) {
        return eventDedupRepository.existsById(eventKey);
    }

    public MealLineEventDedup saveEventDedup(MealLineEventDedup dedup) {
        return eventDedupRepository.saveAndFlush(dedup);
    }

    public Optional<MealLineNotificationOutbox> findOutboxByDedupKey(String dedupKey) {
        return outboxRepository.findByDedupKey(dedupKey);
    }

    public List<MealLineNotificationOutbox> findDueOutbox(Date now) {
        List<String> statuses = List.of("PENDING", "FAILED");
        return outboxRepository.findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                statuses, now);
    }

    public MealLineNotificationOutbox saveOutbox(MealLineNotificationOutbox outbox) {
        return outboxRepository.saveAndFlush(outbox);
    }
}
