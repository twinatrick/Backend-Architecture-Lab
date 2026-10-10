package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.CacheMetricsVo;

import java.util.Map;

public interface ICacheStatsService {
    Map<String, CacheMetricsVo> getCacheStats();
}
