package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Service.IDiscordManagementService;
import com.example.BackendArchitectureLab.Vo.DiscordGfMessageVo;
import com.example.BackendArchitectureLab.Vo.DiscordGfSessionVo;
import com.example.BackendArchitectureLab.Vo.DiscordSubscriptionVo;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DiscordManagementController 單元測試")
class DiscordManagementControllerTest {

    @Mock
    private IDiscordManagementService discordManagementService;

    @InjectMocks
    private DiscordManagementController controller;

    @Test
    @DisplayName("getSessions 應正確呼叫 service 並回傳 200")
    void getSessions_ShouldReturnOk() {
        List<DiscordGfSessionVo> list = List.of(new DiscordGfSessionVo());
        when(discordManagementService.getSessions("g1", true)).thenReturn(list);

        ResponseType<List<DiscordGfSessionVo>> response = controller.getSessions("g1", true);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().size());
        verify(discordManagementService).getSessions("g1", true);
    }

    @Test
    @DisplayName("getSessionById 應正確呼叫 service 並回傳 200")
    void getSessionById_ShouldReturnOk() {
        UUID id = UUID.randomUUID();
        DiscordGfSessionVo vo = new DiscordGfSessionVo();
        when(discordManagementService.getSessionById(id)).thenReturn(vo);

        ResponseType<DiscordGfSessionVo> response = controller.getSessionById(id);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        verify(discordManagementService).getSessionById(id);
    }

    @Test
    @DisplayName("getSessionMessages 應正確呼叫 service 並回傳 200")
    void getSessionMessages_ShouldReturnOk() {
        UUID id = UUID.randomUUID();
        List<DiscordGfMessageVo> list = List.of(new DiscordGfMessageVo());
        when(discordManagementService.getSessionMessages(id)).thenReturn(list);

        ResponseType<List<DiscordGfMessageVo>> response = controller.getSessionMessages(id);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().size());
        verify(discordManagementService).getSessionMessages(id);
    }

    @Test
    @DisplayName("toggleSessionActive 應正確呼叫 service 並回傳 200")
    void toggleSessionActive_ShouldReturnOk() {
        UUID id = UUID.randomUUID();
        DiscordGfSessionVo vo = new DiscordGfSessionVo();
        when(discordManagementService.toggleSessionActive(id)).thenReturn(vo);

        ResponseType<DiscordGfSessionVo> response = controller.toggleSessionActive(id);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        verify(discordManagementService).toggleSessionActive(id);
    }

    @Test
    @DisplayName("deleteSession 應正確呼叫 service 並回傳 200")
    void deleteSession_ShouldReturnOk() {
        UUID id = UUID.randomUUID();

        ResponseType<Void> response = controller.deleteSession(id);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        verify(discordManagementService).deleteSession(id);
    }

    @Test
    @DisplayName("getSubscriptions 應正確呼叫 service 並回傳 200")
    void getSubscriptions_ShouldReturnOk() {
        List<DiscordSubscriptionVo> list = List.of(new DiscordSubscriptionVo());
        when(discordManagementService.getSubscriptions("g1")).thenReturn(list);

        ResponseType<List<DiscordSubscriptionVo>> response = controller.getSubscriptions("g1");

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().size());
        verify(discordManagementService).getSubscriptions("g1");
    }

    @Test
    @DisplayName("deleteSubscription 應正確呼叫 service 並回傳 200")
    void deleteSubscription_ShouldReturnOk() {
        UUID id = UUID.randomUUID();

        ResponseType<Void> response = controller.deleteSubscription(id);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        verify(discordManagementService).deleteSubscription(id);
    }
}
