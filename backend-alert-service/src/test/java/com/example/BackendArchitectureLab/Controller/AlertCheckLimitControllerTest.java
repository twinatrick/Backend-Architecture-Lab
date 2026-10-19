package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Service.IAlertCheckLimitService;
import com.example.BackendArchitectureLab.Vo.AlertCheckLimitVo;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Dto.Query.AlertCheckLimitSearchQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertCheckLimitControllerTest {

    @Mock
    private IAlertCheckLimitService alertCheckLimitService;

    @InjectMocks
    private AlertCheckLimitController controller;

    @Test
    @DisplayName("addLimit 應呼叫 Service 並回傳新增結果")
    void testAddLimit() {
        AlertCheckLimitVo vo = new AlertCheckLimitVo();
        vo.setTableName("aquark_data");
        vo.setColumnName("rain_d");
        vo.setLimitValue(10.0);

        when(alertCheckLimitService.addLimit(vo)).thenReturn(vo);

        ResponseType<AlertCheckLimitVo> response = controller.addLimit(vo);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals(vo, response.getData());
        verify(alertCheckLimitService).addLimit(vo);
    }

    @Test
    @DisplayName("updateLimit 應呼叫 Service 並回傳更新結果")
    void testUpdateLimit() {
        UUID id = UUID.randomUUID();
        AlertCheckLimitVo vo = new AlertCheckLimitVo();
        vo.setId(id);
        vo.setTableName("aquark_data");
        vo.setColumnName("rain_d");
        vo.setLimitValue(20.0);

        when(alertCheckLimitService.updateLimit(vo)).thenReturn(vo);

        ResponseType<AlertCheckLimitVo> response = controller.updateLimit(id, vo);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals(vo, response.getData());
        verify(alertCheckLimitService).updateLimit(vo);
    }

    @Test
    @DisplayName("deleteLimit 應呼叫 Service 執行刪除")
    void testDeleteLimit() {
        UUID id = UUID.randomUUID();

        ResponseType<String> response = controller.deleteLimit(id);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        verify(alertCheckLimitService).deleteLimit(any(AlertCheckLimitVo.class));
    }

    @Test
    @DisplayName("searchAlertCheckLimits 應呼叫 Service 並回傳分頁結果")
    void testSearchAlertCheckLimits() {
        AlertCheckLimitSearchQuery query = new AlertCheckLimitSearchQuery();
        query.setPage(0);
        query.setSize(10);

        AlertCheckLimitVo vo = new AlertCheckLimitVo();
        PageResult<AlertCheckLimitVo> pageResult = PageResult.<AlertCheckLimitVo>builder()
                .content(List.of(vo))
                .totalElements(1L)
                .totalPages(1)
                .currentPage(0)
                .pageSize(10)
                .build();

        when(alertCheckLimitService.searchAlertCheckLimits(query)).thenReturn(pageResult);

        ResponseType<PageResult<AlertCheckLimitVo>> response = controller.searchAlertCheckLimits(query);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals(pageResult, response.getData());
        verify(alertCheckLimitService).searchAlertCheckLimits(query);
    }

    @Test
    @DisplayName("streamAlertCheckLimits 應呼叫 Service 並回傳 SSE 串流")
    void testStreamAlertCheckLimits() {
        ServerSentEvent<List<AlertCheckLimitVo>> chunk = ServerSentEvent.<List<AlertCheckLimitVo>>builder()
                .event("chunk")
                .data(List.of(new AlertCheckLimitVo()))
                .build();
        when(alertCheckLimitService.streamAlertCheckLimitsChunked(250)).thenReturn(Flux.just(chunk));

        Flux<ServerSentEvent<List<AlertCheckLimitVo>>> response = controller.streamAlertCheckLimits(250);

        StepVerifier.create(response)
                .expectNext(chunk)
                .verifyComplete();

        verify(alertCheckLimitService).streamAlertCheckLimitsChunked(250);
    }
}
