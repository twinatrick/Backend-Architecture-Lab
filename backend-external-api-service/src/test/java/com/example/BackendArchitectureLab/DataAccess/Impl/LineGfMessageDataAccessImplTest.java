package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.Entity.LineGfMessage;
import com.example.BackendArchitectureLab.Repository.LineGfMessageRepository;
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
@DisplayName("LineGfMessageDataAccessImpl 單元測試")
class LineGfMessageDataAccessImplTest {

    @Mock
    private LineGfMessageRepository repository;

    private LineGfMessageDataAccessImpl dataAccess;

    @BeforeEach
    void setUp() {
        dataAccess = new LineGfMessageDataAccessImpl(repository);
    }

    @Test
    @DisplayName("save 應代理至 repository")
    void save_ShouldDelegateToRepository() {
        LineGfMessage msg = LineGfMessage.builder().content("LINE訊息").build();
        when(repository.save(msg)).thenReturn(msg);

        LineGfMessage result = dataAccess.save(msg);

        assertEquals(msg, result);
        verify(repository).save(msg);
    }

    @Test
    @DisplayName("findRecentMessages 應將 desc 查詢結果反轉為時間正序 (升冪)")
    void findRecentMessages_ShouldReverseDescResultsToChronologicalOrder() {
        UUID sessionId = UUID.randomUUID();
        LineGfMessage m1 = LineGfMessage.builder().content("早安").build();
        LineGfMessage m2 = LineGfMessage.builder().content("吃飽了嗎").build();

        List<LineGfMessage> descList = new ArrayList<>(List.of(m2, m1));
        when(repository.findTop20BySessionIdOrderByCreatedTimeDesc(sessionId)).thenReturn(descList);

        List<LineGfMessage> result = dataAccess.findRecentMessages(sessionId, 20);

        assertEquals(2, result.size());
        assertEquals("早安", result.get(0).getContent());
        assertEquals("吃飽了嗎", result.get(1).getContent());
    }

    @Test
    @DisplayName("findMessagesBySessionId 應代理至 repository")
    void findMessagesBySessionId_ShouldDelegateToRepository() {
        UUID sessionId = UUID.randomUUID();
        List<LineGfMessage> list = List.of(LineGfMessage.builder().content("歷史紀錄").build());
        when(repository.findBySessionIdOrderByCreatedTimeAsc(sessionId)).thenReturn(list);

        List<LineGfMessage> result = dataAccess.findMessagesBySessionId(sessionId);

        assertEquals(1, result.size());
        verify(repository).findBySessionIdOrderByCreatedTimeAsc(sessionId);
    }

    @Test
    @DisplayName("countBySessionId 應代理至 repository")
    void countBySessionId_ShouldDelegateToRepository() {
        UUID sessionId = UUID.randomUUID();
        when(repository.countBySessionId(sessionId)).thenReturn(7L);

        long count = dataAccess.countBySessionId(sessionId);

        assertEquals(7L, count);
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
