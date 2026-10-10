package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Entity.DiscordGfMessage;

import java.util.List;
import java.util.UUID;

/**
 * DiscordGfMessage 資料存取介面。
 * 抽象 DiscordGfMessageRepository 供 Service 與監聽器使用。
 */
public interface IDiscordGfMessageDataAccess {

    DiscordGfMessage save(DiscordGfMessage message);

    List<DiscordGfMessage> findRecentMessages(UUID sessionId, int limit);

    List<DiscordGfMessage> findMessagesBySessionId(UUID sessionId);

    long countBySessionId(UUID sessionId);

    void deleteBySessionId(UUID sessionId);
}
