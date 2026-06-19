package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Service.ICacheStatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/cache-stats")
public class CacheStatsController {

    @Autowired
    private ICacheStatsService cacheStatsService;

    @GetMapping
    public Map<String, Map<Object, Object>> getCacheStats() {
        return cacheStatsService.getCacheStats();
    }
}
