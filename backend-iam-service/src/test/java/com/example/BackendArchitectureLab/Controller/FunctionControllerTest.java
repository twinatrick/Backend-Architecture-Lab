package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Service.IFunctionCommandService;
import com.example.BackendArchitectureLab.Service.IFunctionHierarchyService;
import com.example.BackendArchitectureLab.Service.IFunctionQueryService;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.FunctionTransVo;
import com.example.BackendArchitectureLab.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Dto.Query.FunctionSearchQuery;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FunctionControllerTest {

    @Mock
    private IFunctionCommandService functionCommandService;

    @Mock
    private IFunctionQueryService functionQueryService;

    @Mock
    private IFunctionHierarchyService functionHierarchyService;

    @InjectMocks
    private FunctionController functionController;

    private FunctionVo testFunction;
    private String testId;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID().toString();
        testFunction = new FunctionVo();
        testFunction.setId(testId);
        testFunction.setName("USER_MANAGEMENT");
    }

    @Test
    @DisplayName("Should add function successfully")
    void testAddFunction() {
        ResponseType<?> response = functionController.addFunction(testFunction);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        verify(functionCommandService).addFunction(testFunction);
    }

    @Test
    @DisplayName("Should update function successfully")
    void testUpdateFunction() {
        ResponseType<String> response = functionController.updateFunction(testId, testFunction);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        verify(functionCommandService).updateFunction(testFunction);
    }

    @Test
    @DisplayName("Should delete function successfully")
    void testDeleteFunction() {
        ResponseType<String> response = functionController.deleteFunction(testId);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        verify(functionCommandService).deleteFunction(any(FunctionVo.class));
    }

    @Test
    @DisplayName("Should stream functions chunked successfully")
    void testStreamFunctions() {
        ServerSentEvent<List<FunctionVo>> chunkEvent = ServerSentEvent.<List<FunctionVo>>builder()
                .event("chunk")
                .id("0")
                .data(List.of(testFunction))
                .build();
        ServerSentEvent<List<FunctionVo>> completeEvent = ServerSentEvent.<List<FunctionVo>>builder()
                .event("complete")
                .data(List.of())
                .build();

        when(functionQueryService.streamFunctionsChunked(anyInt())).thenReturn(Flux.just(chunkEvent, completeEvent));

        Flux<ServerSentEvent<List<FunctionVo>>> stream = functionController.streamFunctions(250);

        StepVerifier.create(stream)
                .expectNext(chunkEvent)
                .expectNext(completeEvent)
                .verifyComplete();
        verify(functionQueryService).streamFunctionsChunked(250);
    }

    @Test
    @DisplayName("Should save all functions successfully")
    void testSaveAllFunction() {
        FunctionTransVo transVo = new FunctionTransVo();
        transVo.setToDelete(List.of());
        transVo.setToSave(List.of());
        transVo.setToSaveChildren(List.of());

        ResponseType<?> response = functionController.saveAllFunction(transVo);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        verify(functionCommandService).deleteFunction(transVo.getToDelete());
        verify(functionCommandService).saveFunction(transVo.getToSave());
        verify(functionHierarchyService).saveFunctionNewChild(transVo.getToSaveChildren());
    }

    @Test
    @DisplayName("Should search functions successfully")
    void testSearchFunctions() {
        FunctionSearchQuery query = new FunctionSearchQuery();
        PageResult<FunctionVo> pageResult = PageResult.<FunctionVo>builder()
                .content(List.of(testFunction))
                .totalElements(1L)
                .currentPage(0)
                .pageSize(10)
                .build();
        when(functionQueryService.searchFunctions(any(FunctionSearchQuery.class))).thenReturn(pageResult);

        ResponseType<PageResult<FunctionVo>> response = functionController.searchFunctions(query);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals(pageResult, response.getData());
        verify(functionQueryService).searchFunctions(query);
    }
}
