package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Entity.LineGfMessage;

import java.util.List;
import java.util.UUID;

/**
 * LineGfMessage 資料存取介面。
 * 抽象 LineGfMessageRepository 供 Service 使用。
 */
public interface ILineGfMessageDataAccess {

    LineGfMessage save(LineGfMessage message);

    List<LineGfMessage> findRecentMessages(UUID sessionId, int limit);

    List<LineGfMessage> findMessagesBySessionId(UUID sessionId);

    long countBySessionId(UUID sessionId);

    void deleteBySessionId(UUID sessionId);
}
