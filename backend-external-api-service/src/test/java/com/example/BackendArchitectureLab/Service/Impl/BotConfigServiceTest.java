package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IBotConfigDataAccess;
import com.example.BackendArchitectureLab.Entity.BotConfig;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Mapper.BotConfigMapper;
import com.example.BackendArchitectureLab.Util.TransactionExecutor;
import com.example.BackendArchitectureLab.Vo.BotConfigVo;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Dto.Query.BotConfigSearchQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BotConfigService 單元測試")
class BotConfigServiceTest {

    @Mock
    private IBotConfigDataAccess botConfigDataAccess;

    @Mock
    private BotConfigMapper botConfigMapper;

    @Mock
    private TransactionExecutor transactionExecutor;

    @InjectMocks
    private BotConfigService botConfigService;

    private UUID testId;
    private BotConfig testEntity;
    private BotConfigVo testVo;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();
        testEntity = new BotConfig();
        testEntity.setId(testId);
        testEntity.setPlatform("DISCORD");
        testEntity.setConfigKey("daily_limit");
        testEntity.setConfigValue("100");
        testEntity.setDescription("每日上限");
        testEntity.setCostLimitDaily(new BigDecimal("100.00"));

        testVo = new BotConfigVo();
        testVo.setId(testId);
        testVo.setPlatform("DISCORD");
        testVo.setConfigKey("daily_limit");
        testVo.setConfigValue("100");
        testVo.setDescription("每日上限");

        lenient().when(transactionExecutor.executeReadOnly(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
    }

    @Test
    @DisplayName("findAll 應回傳所有 BotConfigVo 清單")
    void testFindAll_Success() {
        when(botConfigDataAccess.findAll()).thenReturn(List.of(testEntity));
        when(botConfigMapper.toVo(testEntity)).thenReturn(testVo);

        List<BotConfigVo> result = botConfigService.findAll();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testVo, result.get(0));
        verify(botConfigDataAccess).findAll();
    }

    @Test
    @DisplayName("findById 成功應回傳單筆 BotConfigVo")
    void testFindById_Success() {
        when(botConfigDataAccess.findById(testId)).thenReturn(Optional.of(testEntity));
        when(botConfigMapper.toVo(testEntity)).thenReturn(testVo);

        BotConfigVo result = botConfigService.findById(testId);

        assertNotNull(result);
        assertEquals(testVo, result);
        verify(botConfigDataAccess).findById(testId);
    }

    @Test
    @DisplayName("findById 查無資料應拋出 AppException")
    void testFindById_NotFound_ThrowsAppException() {
        when(botConfigDataAccess.findById(testId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> botConfigService.findById(testId));
        assertEquals(404, ex.getHttpStatus());
        assertEquals("NOT_FOUND", ex.getErrorType());
    }

    @Test
    @DisplayName("create 應成功儲存並回傳 BotConfigVo")
    void testCreate_Success() {
        when(botConfigMapper.toEntity(testVo)).thenReturn(testEntity);
        when(botConfigDataAccess.save(testEntity)).thenReturn(testEntity);
        when(botConfigMapper.toVo(testEntity)).thenReturn(testVo);

        BotConfigVo result = botConfigService.create(testVo);

        assertNotNull(result);
        assertEquals(testVo, result);
        verify(botConfigDataAccess).save(testEntity);
    }

    @Test
    @DisplayName("update 成功應更新並回傳 BotConfigVo")
    void testUpdate_Success() {
        when(botConfigDataAccess.findById(testId)).thenReturn(Optional.of(testEntity));
        when(botConfigMapper.toEntity(testVo)).thenReturn(testEntity);
        when(botConfigDataAccess.save(testEntity)).thenReturn(testEntity);
        when(botConfigMapper.toVo(testEntity)).thenReturn(testVo);

        BotConfigVo result = botConfigService.update(testId, testVo);

        assertNotNull(result);
        assertEquals(testVo, result);
        verify(botConfigDataAccess).save(testEntity);
    }

    @Test
    @DisplayName("update 查無資料應拋出 AppException")
    void testUpdate_NotFound_ThrowsAppException() {
        when(botConfigDataAccess.findById(testId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> botConfigService.update(testId, testVo));
        assertEquals(404, ex.getHttpStatus());
    }

    @Test
    @DisplayName("delete 成功應呼叫 dataAccess 刪除")
    void testDelete_Success() {
        when(botConfigDataAccess.existsById(testId)).thenReturn(true);

        botConfigService.delete(testId);

        verify(botConfigDataAccess).deleteById(testId);
    }

    @Test
    @DisplayName("delete 查無資料應拋出 AppException")
    void testDelete_NotFound_ThrowsAppException() {
        when(botConfigDataAccess.existsById(testId)).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> botConfigService.delete(testId));
        assertEquals(404, ex.getHttpStatus());
    }

    @Test
    @DisplayName("searchBotConfigs 成功應回傳分頁結果")
    void testSearchBotConfigs_Success() {
        BotConfigSearchQuery query = new BotConfigSearchQuery();
        query.setPage(0);
        query.setSize(10);
        query.setSortBy("platform");
        query.setSortDir("asc");

        Page<BotConfig> page = new PageImpl<>(List.of(testEntity));
        when(botConfigDataAccess.searchBotConfigs(query)).thenReturn(page);
        when(botConfigMapper.toVo(testEntity)).thenReturn(testVo);

        PageResult<BotConfigVo> result = botConfigService.searchBotConfigs(query);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(testVo, result.getContent().get(0));
        verify(botConfigDataAccess).searchBotConfigs(query);
    }

    @Test
    @DisplayName("searchBotConfigs 傳入非法排序欄位應拋出 AppException")
    void testSearchBotConfigs_InvalidSortField_ThrowsAppException() {
        BotConfigSearchQuery query = new BotConfigSearchQuery();
        query.setSortBy("invalidField");
        query.setSortDir("asc");

        AppException ex = assertThrows(AppException.class, () -> botConfigService.searchBotConfigs(query));
        assertEquals(400, ex.getHttpStatus());
    }

    @Test
    @DisplayName("streamBotConfigsChunked 成功應發送 chunk 與 complete 事件")
    void testStreamBotConfigsChunked_Success() {
        when(botConfigDataAccess.findAllPaged(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(testEntity)));
        when(botConfigMapper.toVo(testEntity)).thenReturn(testVo);

        StepVerifier.create(botConfigService.streamBotConfigsChunked(250))
                .assertNext(sse -> {
                    assertEquals("chunk", sse.event());
                    assertEquals("0", sse.id());
                    assertNotNull(sse.data());
                    assertEquals(1, sse.data().size());
                    assertEquals("DISCORD", sse.data().get(0).getPlatform());
                })
                .assertNext(sse -> {
                    assertEquals("complete", sse.event());
                })
                .verifyComplete();

        verify(botConfigDataAccess).findAllPaged(any(Pageable.class));
    }

    @Test
    @DisplayName("streamBotConfigsChunked 空資料應直接發送 complete 事件")
    void testStreamBotConfigsChunked_Empty() {
        when(botConfigDataAccess.findAllPaged(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        StepVerifier.create(botConfigService.streamBotConfigsChunked(250))
                .assertNext(sse -> {
                    assertEquals("complete", sse.event());
                })
                .verifyComplete();

        verify(botConfigDataAccess).findAllPaged(any(Pageable.class));
    }

    @Test
    @DisplayName("streamBotConfigsChunked 發生例外應發送 error 事件")
    void testStreamBotConfigsChunked_Error() {
        when(botConfigDataAccess.findAllPaged(any(Pageable.class)))
                .thenThrow(new RuntimeException("Database error"));

        StepVerifier.create(botConfigService.streamBotConfigsChunked(250))
                .assertNext(sse -> {
                    assertEquals("error", sse.event());
                })
                .verifyComplete();
    }
}
