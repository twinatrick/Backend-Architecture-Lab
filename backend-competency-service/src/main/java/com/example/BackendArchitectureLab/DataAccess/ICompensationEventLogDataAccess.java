package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Entity.CompensationEventLog;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ICompensationEventLogDataAccess - 補償消費事件紀錄（compensation_event_log）資料存取層。
 */
public interface ICompensationEventLogDataAccess {

    CompensationEventLog save(CompensationEventLog entity);

    CompensationEventLog saveAndFlush(CompensationEventLog entity);

    Optional<CompensationEventLog> findByEventId(UUID eventId);

    List<CompensationEventLog> findTop20ByStatusOrderByUpdatedTimeDesc(String status);

    List<CompensationEventLog> findTop20ByStatusAndFailedAtAfter(String status, Date failedAt);

    List<CompensationEventLog> findTop50ByStatusAndLeaseUntilBeforeOrderByLeaseUntilAsc(String status, Date leaseUntil);

    List<CompensationEventLog> findTop50ByStatusAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(String status, Date nextAttemptAt);

    int retryClaim(UUID eventId, String processing, String failed, String ownerId,
                   Date processingAt, Date leaseUntil, Date now);

    int reclaimLease(UUID eventId, String processing, Date now, String ownerId,
                     Date processingAt, Date leaseUntil);

    int markState(UUID eventId, String ownerId, Long fencingVersion, String processing,
                  String status, Date processedAt, Date failedAt, String lastError, Date nextAttemptAt);
}
