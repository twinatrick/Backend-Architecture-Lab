package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.Entity.DiscordGfMessage;
import com.example.BackendArchitectureLab.Repository.DiscordGfMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DiscordGfMessageDataAccessImpl 單元測試")
class DiscordGfMessageDataAccessImplTest {

    @Mock
    private DiscordGfMessageRepository repository;

    private DiscordGfMessageDataAccessImpl dataAccess;

    @BeforeEach
    void setUp() {
        dataAccess = new DiscordGfMessageDataAccessImpl(repository);
    }

    @Test
    @DisplayName("save 應代理至 repository")
    void save_ShouldDelegateToRepository() {
        DiscordGfMessage msg = DiscordGfMessage.builder().content("哈囉").build();
        when(repository.save(msg)).thenReturn(msg);

        DiscordGfMessage result = dataAccess.save(msg);

        assertEquals(msg, result);
        verify(repository).save(msg);
    }

    @Test
    @DisplayName("findRecentMessages 應將 desc 查詢結果反轉為時間正序 (升冪)")
    void findRecentMessages_ShouldReverseDescResultsToChronologicalOrder() {
        UUID sessionId = UUID.randomUUID();
        DiscordGfMessage m1 = DiscordGfMessage.builder().content("第一句").build();
        DiscordGfMessage m2 = DiscordGfMessage.builder().content("第二句").build();

        // repository returns DESC order (m2 newest, m1 older)
        List<DiscordGfMessage> descList = new ArrayList<>(List.of(m2, m1));
        when(repository.findTop20BySessionIdOrderByCreatedTimeDesc(sessionId)).thenReturn(descList);

        List<DiscordGfMessage> result = dataAccess.findRecentMessages(sessionId, 20);

        assertEquals(2, result.size());
        assertEquals("第一句", result.get(0).getContent());
        assertEquals("第二句", result.get(1).getContent());
    }

    @Test
    @DisplayName("findMessagesBySessionId 應代理至 repository")
    void findMessagesBySessionId_ShouldDelegateToRepository() {
        UUID sessionId = UUID.randomUUID();
        List<DiscordGfMessage> list = List.of(DiscordGfMessage.builder().content("紀錄").build());
        when(repository.findBySessionIdOrderByCreatedTimeAsc(sessionId)).thenReturn(list);

        List<DiscordGfMessage> result = dataAccess.findMessagesBySessionId(sessionId);

        assertEquals(1, result.size());
        verify(repository).findBySessionIdOrderByCreatedTimeAsc(sessionId);
    }

    @Test
    @DisplayName("countBySessionId 應代理至 repository")
    void countBySessionId_ShouldDelegateToRepository() {
        UUID sessionId = UUID.randomUUID();
        when(repository.countBySessionId(sessionId)).thenReturn(10L);

        long count = dataAccess.countBySessionId(sessionId);

        assertEquals(10L, count);
        verify(repository).countBySessionId(sessionId);
    }

    @Test
    @DisplayName("deleteBySessionId 應代理至 repository")
    void deleteBySessionId_ShouldDelegateToRepository() {
        UUID sessionId = UUID.randomUUID();

        dataAccess.deleteBySessionId(sessionId);

        verify(repository).deleteBySessionId(sessionId);
    }
}
