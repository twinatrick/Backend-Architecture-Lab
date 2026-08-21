package com.example.BackendArchitectureLab.Vo;

/**
 * CacheStatsEvent - 快取命中與指標統計事件。
 */
public record CacheStatsEvent(
        String cacheName,
        String field
) {
}
