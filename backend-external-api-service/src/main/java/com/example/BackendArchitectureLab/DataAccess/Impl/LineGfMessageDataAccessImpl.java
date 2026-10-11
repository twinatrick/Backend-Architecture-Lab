package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.DataAccess.ILineGfMessageDataAccess;
import com.example.BackendArchitectureLab.Entity.LineGfMessage;
import com.example.BackendArchitectureLab.Repository.LineGfMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * ILineGfMessageDataAccess 實作。
 * 委派 LineGfMessageRepository 執行資料存取。
 */
@Component
@RequiredArgsConstructor
public class LineGfMessageDataAccessImpl implements ILineGfMessageDataAccess {

    private final LineGfMessageRepository lineGfMessageRepository;

    @Override
    public LineGfMessage save(LineGfMessage message) {
        return lineGfMessageRepository.save(message);
    }

    @Override
    public List<LineGfMessage> findRecentMessages(UUID sessionId, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        List<LineGfMessage> recentDesc = lineGfMessageRepository.findBySessionIdOrderByCreatedTimeDesc(
                sessionId, PageRequest.of(0, limit)
        );
        if (recentDesc == null || recentDesc.isEmpty()) {
            return Collections.emptyList();
        }
        List<LineGfMessage> chronological = new ArrayList<>(recentDesc);
        Collections.reverse(chronological);
        return chronological;
    }

    @Override
    public List<LineGfMessage> findMessagesBySessionId(UUID sessionId) {
        return lineGfMessageRepository.findBySessionIdOrderByCreatedTimeAsc(sessionId);
    }

    @Override
    public long countBySessionId(UUID sessionId) {
        return lineGfMessageRepository.countBySessionId(sessionId);
    }

    @Override
    @Transactional
    public void deleteBySessionId(UUID sessionId) {
        lineGfMessageRepository.deleteBySessionId(sessionId);
    }
}
