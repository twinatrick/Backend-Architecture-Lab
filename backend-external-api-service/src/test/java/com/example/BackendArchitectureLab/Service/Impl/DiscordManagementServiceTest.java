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
import com.example.BackendArchitectureLab.Vo.DiscordGfMessageVo;
import com.example.BackendArchitectureLab.Vo.DiscordGfSessionVo;
import com.example.BackendArchitectureLab.Vo.DiscordSubscriptionVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

    private DiscordManagementService service;

    @BeforeEach
    void setUp() {
        service = new DiscordManagementService(
                discordGfSessionDataAccess,
                discordGfMessageDataAccess,
                discordSubscriptionDataAccess,
                gfSessionMapper,
                discordGfMessageMapper,
                discordSubscriptionMapper
        );
    }

    @Test
    @DisplayName("getSessions: 帶有 guildId 與 active 參數應查詢 findByGuildIdAndActive")
    void getSessions_WithGuildIdAndActive_ShouldFilterBoth() {
        List<DiscordGfSession> sessions = List.of(new DiscordGfSession());
        List<DiscordGfSessionVo> vos = List.of(new DiscordGfSessionVo());
        when(discordGfSessionDataAccess.findByGuildIdAndActive("g1", true)).thenReturn(sessions);
        when(gfSessionMapper.toDiscordVoList(sessions)).thenReturn(vos);

        List<DiscordGfSessionVo> result = service.getSessions("g1", true);

        assertEquals(1, result.size());
        verify(discordGfSessionDataAccess).findByGuildIdAndActive("g1", true);
    }

    @Test
    @DisplayName("getSessions: 僅有 guildId 參數應查詢 findByGuildId")
    void getSessions_WithGuildIdOnly_ShouldFilterByGuildId() {
        List<DiscordGfSession> sessions = List.of(new DiscordGfSession());
        List<DiscordGfSessionVo> vos = List.of(new DiscordGfSessionVo());
        when(discordGfSessionDataAccess.findByGuildId("g1")).thenReturn(sessions);
        when(gfSessionMapper.toDiscordVoList(sessions)).thenReturn(vos);

        List<DiscordGfSessionVo> result = service.getSessions("g1", null);

        assertEquals(1, result.size());
        verify(discordGfSessionDataAccess).findByGuildId("g1");
    }

    @Test
    @DisplayName("getSessions: 僅有 active 參數應查詢 findByActive")
    void getSessions_WithActiveOnly_ShouldFilterByActive() {
        List<DiscordGfSession> sessions = List.of(new DiscordGfSession());
        List<DiscordGfSessionVo> vos = List.of(new DiscordGfSessionVo());
        when(discordGfSessionDataAccess.findByActive(false)).thenReturn(sessions);
        when(gfSessionMapper.toDiscordVoList(sessions)).thenReturn(vos);

        List<DiscordGfSessionVo> result = service.getSessions(null, false);

        assertEquals(1, result.size());
        verify(discordGfSessionDataAccess).findByActive(false);
    }

    @Test
    @DisplayName("getSessions: 無參數應查詢 findAll")
    void getSessions_WithNoFilter_ShouldFindAll() {
        List<DiscordGfSession> sessions = List.of(new DiscordGfSession());
        List<DiscordGfSessionVo> vos = List.of(new DiscordGfSessionVo());
        when(discordGfSessionDataAccess.findAll()).thenReturn(sessions);
        when(gfSessionMapper.toDiscordVoList(sessions)).thenReturn(vos);

        List<DiscordGfSessionVo> result = service.getSessions(null, null);

        assertEquals(1, result.size());
        verify(discordGfSessionDataAccess).findAll();
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
    @DisplayName("deleteSession: 成功刪除會話")
    void deleteSession_Success() {
        UUID id = UUID.randomUUID();
        DiscordGfSession session = new DiscordGfSession();
        when(discordGfSessionDataAccess.findById(id)).thenReturn(Optional.of(session));

        service.deleteSession(id);

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
    @DisplayName("getSubscriptions: 帶有 guildId 應篩選")
    void getSubscriptions_WithGuildId() {
        List<DiscordSubscription> list = List.of(new DiscordSubscription());
        List<DiscordSubscriptionVo> vos = List.of(new DiscordSubscriptionVo());
        when(discordSubscriptionDataAccess.findByGuildId("g1")).thenReturn(list);
        when(discordSubscriptionMapper.toVoList(list)).thenReturn(vos);

        List<DiscordSubscriptionVo> result = service.getSubscriptions("g1");

        assertEquals(1, result.size());
        verify(discordSubscriptionDataAccess).findByGuildId("g1");
    }

    @Test
    @DisplayName("getSubscriptions: 無 guildId 應查詢全部")
    void getSubscriptions_All() {
        List<DiscordSubscription> list = List.of(new DiscordSubscription());
        List<DiscordSubscriptionVo> vos = List.of(new DiscordSubscriptionVo());
        when(discordSubscriptionDataAccess.findAll()).thenReturn(list);
        when(discordSubscriptionMapper.toVoList(list)).thenReturn(vos);

        List<DiscordSubscriptionVo> result = service.getSubscriptions(null);

        assertEquals(1, result.size());
        verify(discordSubscriptionDataAccess).findAll();
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
