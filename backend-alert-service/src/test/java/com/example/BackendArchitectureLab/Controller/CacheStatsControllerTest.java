package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Service.ICacheStatsService;
import com.example.BackendArchitectureLab.Vo.CacheMetricsVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CacheStatsControllerTest {

    @Mock
    private ICacheStatsService cacheStatsService;

    @InjectMocks
    private CacheStatsController cacheStatsController;

    @Test
    @DisplayName("getCacheStats 應委派 Service 並回傳快取度量 Map")
    void testGetCacheStats() {
        CacheMetricsVo metrics = new CacheMetricsVo(10L, 2L, 0L, 0L, 12L, 10f / 12f);
        when(cacheStatsService.getCacheStats()).thenReturn(Map.of("users", metrics));

        Map<String, CacheMetricsVo> result = cacheStatsController.getCacheStats();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(metrics, result.get("users"));
        verify(cacheStatsService).getCacheStats();
    }
}
