package com.example.BackendArchitectureLab.Service;

import java.util.Map;

public interface ICacheStatsService {
    Map<String, Map<Object, Object>> getCacheStats();
}
