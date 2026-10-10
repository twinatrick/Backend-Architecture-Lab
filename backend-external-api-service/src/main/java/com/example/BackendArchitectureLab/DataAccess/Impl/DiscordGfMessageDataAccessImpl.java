package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.DataAccess.IDiscordGfMessageDataAccess;
import com.example.BackendArchitectureLab.Entity.DiscordGfMessage;
import com.example.BackendArchitectureLab.Repository.DiscordGfMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * IDiscordGfMessageDataAccess 實作。
 * 委派 DiscordGfMessageRepository 執行資料存取。
 */
@Component
@RequiredArgsConstructor
public class DiscordGfMessageDataAccessImpl implements IDiscordGfMessageDataAccess {

    private final DiscordGfMessageRepository discordGfMessageRepository;

    @Override
    public DiscordGfMessage save(DiscordGfMessage message) {
        return discordGfMessageRepository.save(message);
    }

    @Override
    public List<DiscordGfMessage> findRecentMessages(UUID sessionId, int limit) {
        List<DiscordGfMessage> recentDesc = discordGfMessageRepository.findTop20BySessionIdOrderByCreatedTimeDesc(sessionId);
        if (recentDesc == null || recentDesc.isEmpty()) {
            return Collections.emptyList();
        }
        List<DiscordGfMessage> chronological = new ArrayList<>(recentDesc);
        Collections.reverse(chronological);
        return chronological;
    }

    @Override
    public List<DiscordGfMessage> findMessagesBySessionId(UUID sessionId) {
        return discordGfMessageRepository.findBySessionIdOrderByCreatedTimeAsc(sessionId);
    }

    @Override
    public long countBySessionId(UUID sessionId) {
        return discordGfMessageRepository.countBySessionId(sessionId);
    }

    @Override
    public void deleteBySessionId(UUID sessionId) {
        discordGfMessageRepository.deleteBySessionId(sessionId);
    }
}
