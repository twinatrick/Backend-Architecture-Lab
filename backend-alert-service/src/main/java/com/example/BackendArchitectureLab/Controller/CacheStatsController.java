package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Service.ICacheStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/cache-stats")
@RequiredArgsConstructor
@ApiControllerTag(name = "Cache Stats", description = "快取統計查詢相關 API")
public class CacheStatsController {

    private final ICacheStatsService cacheStatsService;

    @GetMapping
    @ApiOperationOk(summary = "取得快取統計", description = "回傳所有快取區域的統計資料。")
    public Map<String, Map<Object, Object>> getCacheStats() {
        return cacheStatsService.getCacheStats();
    }
}
