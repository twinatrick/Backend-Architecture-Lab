package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Service.IDiscordManagementService;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.DiscordGfMessageVo;
import com.example.BackendArchitectureLab.Vo.DiscordGfSessionVo;
import com.example.BackendArchitectureLab.Vo.DiscordSubscriptionVo;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Dto.Query.DiscordGfSessionSearchQuery;
import com.example.BackendArchitectureLab.Dto.Query.DiscordSubscriptionSearchQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

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
    @DisplayName("searchSessions 應正確呼叫 service 並回傳 200")
    void searchSessions_ShouldReturnOk() {
        DiscordGfSessionSearchQuery query = new DiscordGfSessionSearchQuery();
        PageResult<DiscordGfSessionVo> pageResult = PageResult.of(
                new PageImpl<>(List.of(new DiscordGfSessionVo()), PageRequest.of(0, 10), 1),
                List.of(new DiscordGfSessionVo())
        );
        when(discordManagementService.searchSessions(query)).thenReturn(pageResult);

        ResponseType<PageResult<DiscordGfSessionVo>> response = controller.searchSessions(query);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().getContent().size());
        verify(discordManagementService).searchSessions(query);
    }

    @Test
    @DisplayName("streamSessions 應正確委派 service 串流 Flux")
    void streamSessions_ShouldReturnFlux() {
        ServerSentEvent<List<DiscordGfSessionVo>> sse = ServerSentEvent.<List<DiscordGfSessionVo>>builder()
                .event("complete")
                .build();
        when(discordManagementService.streamSessionsChunked(250)).thenReturn(Flux.just(sse));

        Flux<ServerSentEvent<List<DiscordGfSessionVo>>> result = controller.streamSessions(250);

        assertNotNull(result);
        verify(discordManagementService).streamSessionsChunked(250);
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
    @DisplayName("searchSubscriptions 應正確呼叫 service 並回傳 200")
    void searchSubscriptions_ShouldReturnOk() {
        DiscordSubscriptionSearchQuery query = new DiscordSubscriptionSearchQuery();
        PageResult<DiscordSubscriptionVo> pageResult = PageResult.of(
                new PageImpl<>(List.of(new DiscordSubscriptionVo()), PageRequest.of(0, 10), 1),
                List.of(new DiscordSubscriptionVo())
        );
        when(discordManagementService.searchSubscriptions(query)).thenReturn(pageResult);

        ResponseType<PageResult<DiscordSubscriptionVo>> response = controller.searchSubscriptions(query);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().getContent().size());
        verify(discordManagementService).searchSubscriptions(query);
    }

    @Test
    @DisplayName("streamSubscriptions 應正確委派 service 串流 Flux")
    void streamSubscriptions_ShouldReturnFlux() {
        ServerSentEvent<List<DiscordSubscriptionVo>> sse = ServerSentEvent.<List<DiscordSubscriptionVo>>builder()
                .event("complete")
                .build();
        when(discordManagementService.streamSubscriptionsChunked(250)).thenReturn(Flux.just(sse));

        Flux<ServerSentEvent<List<DiscordSubscriptionVo>>> result = controller.streamSubscriptions(250);

        assertNotNull(result);
        verify(discordManagementService).streamSubscriptionsChunked(250);
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
