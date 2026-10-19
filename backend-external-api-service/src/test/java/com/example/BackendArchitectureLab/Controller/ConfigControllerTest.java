package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Service.IBotConfigService;
import com.example.BackendArchitectureLab.Service.IExternalPlatformStatusService;
import com.example.BackendArchitectureLab.Vo.BotConfigVo;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.ExternalPlatformStatusVo;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Dto.Query.BotConfigSearchQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConfigController 單元測試")
class ConfigControllerTest {

    @Mock
    private IBotConfigService botConfigService;

    @Mock
    private IExternalPlatformStatusService externalPlatformStatusService;

    @InjectMocks
    private ConfigController controller;

    @Test
    @DisplayName("search 應分頁回傳 BotConfigVo 清單")
    void search_ShouldReturnPageResult() {
        BotConfigSearchQuery query = new BotConfigSearchQuery();
        PageResult<BotConfigVo> pageResult = PageResult.of(new PageImpl<>(List.of(new BotConfigVo())));
        when(botConfigService.searchBotConfigs(query)).thenReturn(pageResult);

        ResponseType<PageResult<BotConfigVo>> response = controller.search(query);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().getContent().size());
        verify(botConfigService).searchBotConfigs(query);
    }

    @Test
    @DisplayName("streamBotConfigs 應回傳 SSE Flux")
    void streamBotConfigs_ShouldReturnFlux() {
        ServerSentEvent<List<BotConfigVo>> chunkEvent = ServerSentEvent.<List<BotConfigVo>>builder()
                .event("chunk")
                .data(List.of(new BotConfigVo()))
                .build();
        when(botConfigService.streamBotConfigsChunked(250)).thenReturn(Flux.just(chunkEvent));

        Flux<ServerSentEvent<List<BotConfigVo>>> flux = controller.streamBotConfigs(250);

        StepVerifier.create(flux)
                .expectNext(chunkEvent)
                .verifyComplete();
        verify(botConfigService).streamBotConfigsChunked(250);
    }

    @Test
    @DisplayName("getStatus 應回傳平台狀態 ExternalPlatformStatusVo")
    void getStatus_ShouldReturnPlatformStatus() {
        ExternalPlatformStatusVo vo = ExternalPlatformStatusVo.builder().build();
        when(externalPlatformStatusService.getPlatformStatus()).thenReturn(vo);

        ResponseType<ExternalPlatformStatusVo> response = controller.getStatus();

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals(vo, response.getData());
        verify(externalPlatformStatusService).getPlatformStatus();
    }

    @Test
    @DisplayName("getById 應依 ID 回傳單筆 BotConfigVo")
    void getById_ShouldReturnVo() {
        UUID id = UUID.randomUUID();
        BotConfigVo vo = new BotConfigVo();
        when(botConfigService.findById(id)).thenReturn(vo);

        ResponseType<BotConfigVo> response = controller.getById(id);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        verify(botConfigService).findById(id);
    }

    @Test
    @DisplayName("create 應建立並回傳 BotConfigVo")
    void create_ShouldReturnVo() {
        BotConfigVo vo = new BotConfigVo();
        when(botConfigService.create(vo)).thenReturn(vo);

        ResponseType<BotConfigVo> response = controller.create(vo);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        verify(botConfigService).create(vo);
    }

    @Test
    @DisplayName("update 應更新並回傳 BotConfigVo")
    void update_ShouldReturnVo() {
        UUID id = UUID.randomUUID();
        BotConfigVo vo = new BotConfigVo();
        when(botConfigService.update(id, vo)).thenReturn(vo);

        ResponseType<BotConfigVo> response = controller.update(id, vo);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        verify(botConfigService).update(id, vo);
    }

    @Test
    @DisplayName("delete 應依 ID 刪除")
    void delete_ShouldReturnOk() {
        UUID id = UUID.randomUUID();

        ResponseType<Void> response = controller.delete(id);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        verify(botConfigService).delete(id);
    }
}
