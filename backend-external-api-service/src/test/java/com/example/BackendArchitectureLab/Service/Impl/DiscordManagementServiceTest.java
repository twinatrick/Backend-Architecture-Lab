package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IDiscordGfMessageDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IDiscordGfSessionDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IDiscordSubscriptionDataAccess;
import com.example.BackendArchitectureLab.Entity.DiscordGfMessage;
import com.example.BackendArchitectureLab.Entity.DiscordGfSession;
import com.example.BackendArchitectureLab.Entity.DiscordSubscription;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Mapper.DiscordGfMessageMapper;
import com.example.BackendArchitectureLab.Mapper.DiscordSubscriptionMapper;
import com.example.BackendArchitectureLab.Mapper.GfSessionMapper;
import com.example.BackendArchitectureLab.Util.TransactionExecutor;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.DiscordGfMessageVo;
import com.example.BackendArchitectureLab.Vo.DiscordGfSessionVo;
import com.example.BackendArchitectureLab.Vo.DiscordSubscriptionVo;
import com.example.BackendArchitectureLab.Dto.Query.DiscordGfSessionSearchQuery;
import com.example.BackendArchitectureLab.Dto.Query.DiscordSubscriptionSearchQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.codec.ServerSentEvent;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DiscordManagementService 單元測試")
class DiscordManagementServiceTest {

    @Mock
    private IDiscordGfSessionDataAccess discordGfSessionDataAccess;

    @Mock
    private IDiscordGfMessageDataAccess discordGfMessageDataAccess;

    @Mock
    private IDiscordSubscriptionDataAccess discordSubscriptionDataAccess;

    @Mock
    private GfSessionMapper gfSessionMapper;

    @Mock
    private DiscordGfMessageMapper discordGfMessageMapper;

    @Mock
    private DiscordSubscriptionMapper discordSubscriptionMapper;

    @Mock
    private TransactionExecutor transactionExecutor;

    private DiscordManagementService service;

    @BeforeEach
    void setUp() {
        lenient().when(transactionExecutor.executeReadOnly(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });

        service = new DiscordManagementService(
                discordGfSessionDataAccess,
                discordGfMessageDataAccess,
                discordSubscriptionDataAccess,
                gfSessionMapper,
                discordGfMessageMapper,
                discordSubscriptionMapper,
                transactionExecutor
        );
    }

    @Test
    @DisplayName("searchSessions: 正常搜尋分頁")
    void searchSessions_Success() {
        DiscordGfSessionSearchQuery query = new DiscordGfSessionSearchQuery();
        query.setPage(0);
        query.setSize(10);
        query.setSortBy("createdTime");
        query.setSortDir("desc");

        DiscordGfSession entity = new DiscordGfSession();
        DiscordGfSessionVo vo = new DiscordGfSessionVo();
        Page<DiscordGfSession> page = new PageImpl<>(List.of(entity), PageRequest.of(0, 10), 1);

        when(discordGfSessionDataAccess.searchSessions(query)).thenReturn(page);
        when(gfSessionMapper.toDiscordVoList(List.of(entity))).thenReturn(List.of(vo));

        PageResult<DiscordGfSessionVo> result = service.searchSessions(query);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(1L, result.getTotalElements());
        verify(discordGfSessionDataAccess).searchSessions(query);
    }

    @Test
    @DisplayName("searchSessions: 非法排序欄位應拋出 400")
    void searchSessions_InvalidSortBy_ShouldThrowException() {
        DiscordGfSessionSearchQuery query = new DiscordGfSessionSearchQuery();
        query.setSortBy("invalidField");

        AppException ex = assertThrows(AppException.class, () -> service.searchSessions(query));
        assertEquals(400, ex.getHttpStatus());
    }

    @Test
    @DisplayName("streamSessionsChunked: 成功串流分塊資料")
    void streamSessionsChunked_Success() {
        DiscordGfSession entity = new DiscordGfSession();
        DiscordGfSessionVo vo = new DiscordGfSessionVo();
        Page<DiscordGfSession> page = new PageImpl<>(List.of(entity), PageRequest.of(0, 250), 1);

        when(discordGfSessionDataAccess.findAllPaged(any())).thenReturn(page);
        when(gfSessionMapper.toVo(entity)).thenReturn(vo);

        StepVerifier.create(service.streamSessionsChunked(250))
                .assertNext(sse -> {
                    assertEquals("chunk", sse.event());
                    assertEquals("0", sse.id());
                    assertNotNull(sse.data());
                    assertEquals(1, sse.data().size());
                })
                .assertNext(sse -> assertEquals("complete", sse.event()))
                .verifyComplete();
    }

    @Test
    @DisplayName("streamSessionsChunked: 空資料應直接發送 complete")
    void streamSessionsChunked_Empty() {
        Page<DiscordGfSession> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 250), 0);
        when(discordGfSessionDataAccess.findAllPaged(any())).thenReturn(emptyPage);

        StepVerifier.create(service.streamSessionsChunked(250))
                .assertNext(sse -> assertEquals("complete", sse.event()))
                .verifyComplete();
    }

    @Test
    @DisplayName("searchSubscriptions: 正常搜尋分頁")
    void searchSubscriptions_Success() {
        DiscordSubscriptionSearchQuery query = new DiscordSubscriptionSearchQuery();
        query.setPage(0);
        query.setSize(10);
        query.setSortBy("createdTime");
        query.setSortDir("desc");

        DiscordSubscription entity = new DiscordSubscription();
        DiscordSubscriptionVo vo = new DiscordSubscriptionVo();
        Page<DiscordSubscription> page = new PageImpl<>(List.of(entity), PageRequest.of(0, 10), 1);

        when(discordSubscriptionDataAccess.searchSubscriptions(query)).thenReturn(page);
        when(discordSubscriptionMapper.toVoList(List.of(entity))).thenReturn(List.of(vo));

        PageResult<DiscordSubscriptionVo> result = service.searchSubscriptions(query);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(1L, result.getTotalElements());
        verify(discordSubscriptionDataAccess).searchSubscriptions(query);
    }

    @Test
    @DisplayName("searchSubscriptions: 非法排序欄位應拋出 400")
    void searchSubscriptions_InvalidSortBy_ShouldThrowException() {
        DiscordSubscriptionSearchQuery query = new DiscordSubscriptionSearchQuery();
        query.setSortBy("invalidField");

        AppException ex = assertThrows(AppException.class, () -> service.searchSubscriptions(query));
        assertEquals(400, ex.getHttpStatus());
    }

    @Test
    @DisplayName("streamSubscriptionsChunked: 成功串流分塊資料")
    void streamSubscriptionsChunked_Success() {
        DiscordSubscription entity = new DiscordSubscription();
        DiscordSubscriptionVo vo = new DiscordSubscriptionVo();
        Page<DiscordSubscription> page = new PageImpl<>(List.of(entity), PageRequest.of(0, 250), 1);

        when(discordSubscriptionDataAccess.findAllPaged(any())).thenReturn(page);
        when(discordSubscriptionMapper.toVo(entity)).thenReturn(vo);

        StepVerifier.create(service.streamSubscriptionsChunked(250))
                .assertNext(sse -> {
                    assertEquals("chunk", sse.event());
                    assertEquals("0", sse.id());
                    assertNotNull(sse.data());
                    assertEquals(1, sse.data().size());
                })
                .assertNext(sse -> assertEquals("complete", sse.event()))
                .verifyComplete();
    }

    @Test
    @DisplayName("streamSubscriptionsChunked: 空資料應直接發送 complete")
    void streamSubscriptionsChunked_Empty() {
        Page<DiscordSubscription> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 250), 0);
        when(discordSubscriptionDataAccess.findAllPaged(any())).thenReturn(emptyPage);

        StepVerifier.create(service.streamSubscriptionsChunked(250))
                .assertNext(sse -> assertEquals("complete", sse.event()))
                .verifyComplete();
    }

    @Test
    @DisplayName("getSessionById: 成功取得單筆會話")
    void getSessionById_Success() {
        UUID id = UUID.randomUUID();
        DiscordGfSession session = new DiscordGfSession();
        DiscordGfSessionVo vo = new DiscordGfSessionVo();
        when(discordGfSessionDataAccess.findById(id)).thenReturn(Optional.of(session));
        when(gfSessionMapper.toVo(session)).thenReturn(vo);

        DiscordGfSessionVo result = service.getSessionById(id);

        assertNotNull(result);
        verify(discordGfSessionDataAccess).findById(id);
    }

    @Test
    @DisplayName("getSessionById: 查無資料應拋出 AppException 404")
    void getSessionById_NotFound_ShouldThrowException() {
        UUID id = UUID.randomUUID();
        when(discordGfSessionDataAccess.findById(id)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> service.getSessionById(id));
        assertEquals(404, ex.getHttpStatus());
    }

    @Test
    @DisplayName("getSessionMessages: 成功取得訊息列表")
    void getSessionMessages_Success() {
        UUID id = UUID.randomUUID();
        DiscordGfSession session = new DiscordGfSession();
        List<DiscordGfMessage> messages = List.of(new DiscordGfMessage());
        List<DiscordGfMessageVo> vos = List.of(new DiscordGfMessageVo());
        when(discordGfSessionDataAccess.findById(id)).thenReturn(Optional.of(session));
        when(discordGfMessageDataAccess.findMessagesBySessionId(id)).thenReturn(messages);
        when(discordGfMessageMapper.toVoList(messages)).thenReturn(vos);

        List<DiscordGfMessageVo> result = service.getSessionMessages(id);

        assertEquals(1, result.size());
        verify(discordGfMessageDataAccess).findMessagesBySessionId(id);
    }

    @Test
    @DisplayName("getSessionMessages: 會話不存在應拋出 404")
    void getSessionMessages_NotFound_ShouldThrowException() {
        UUID id = UUID.randomUUID();
        when(discordGfSessionDataAccess.findById(id)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> service.getSessionMessages(id));
        assertEquals(404, ex.getHttpStatus());
    }

    @Test
    @DisplayName("toggleSessionActive: 應成功反轉 active 狀態")
    void toggleSessionActive_Success() {
        UUID id = UUID.randomUUID();
        DiscordGfSession session = new DiscordGfSession();
        session.setActive(true);
        DiscordGfSessionVo vo = new DiscordGfSessionVo();

        when(discordGfSessionDataAccess.findById(id)).thenReturn(Optional.of(session));
        when(discordGfSessionDataAccess.save(any(DiscordGfSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(gfSessionMapper.toVo(any(DiscordGfSession.class))).thenReturn(vo);

        DiscordGfSessionVo result = service.toggleSessionActive(id);

        assertNotNull(result);
        assertFalse(session.getActive());
        verify(discordGfSessionDataAccess).save(session);
    }

    @Test
    @DisplayName("toggleSessionActive: 查無會話應拋出 404")
    void toggleSessionActive_NotFound() {
        UUID id = UUID.randomUUID();
        when(discordGfSessionDataAccess.findById(id)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> service.toggleSessionActive(id));
        assertEquals(404, ex.getHttpStatus());
    }

    @Test
    @DisplayName("deleteSession: 成功刪除會話並級聯清理歷史訊息")
    void deleteSession_Success() {
        UUID id = UUID.randomUUID();
        DiscordGfSession session = new DiscordGfSession();
        when(discordGfSessionDataAccess.findById(id)).thenReturn(Optional.of(session));

        service.deleteSession(id);

        verify(discordGfMessageDataAccess).deleteBySessionId(id);
        verify(discordGfSessionDataAccess).delete(session);
    }

    @Test
    @DisplayName("deleteSession: 查無會話應拋出 404")
    void deleteSession_NotFound() {
        UUID id = UUID.randomUUID();
        when(discordGfSessionDataAccess.findById(id)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> service.deleteSession(id));
        assertEquals(404, ex.getHttpStatus());
    }

    @Test
    @DisplayName("deleteSubscription: 成功刪除訂閱")
    void deleteSubscription_Success() {
        UUID id = UUID.randomUUID();
        DiscordSubscription sub = new DiscordSubscription();
        when(discordSubscriptionDataAccess.findById(id)).thenReturn(Optional.of(sub));

        service.deleteSubscription(id);

        verify(discordSubscriptionDataAccess).delete(sub);
    }

    @Test
    @DisplayName("deleteSubscription: 查無訂閱應拋出 404")
    void deleteSubscription_NotFound() {
        UUID id = UUID.randomUUID();
        when(discordSubscriptionDataAccess.findById(id)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> service.deleteSubscription(id));
        assertEquals(404, ex.getHttpStatus());
    }
}
