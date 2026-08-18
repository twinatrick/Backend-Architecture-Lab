package com.example.BackendArchitectureLab.Vo;

public record CacheStatsEvent(
        String cacheName,
        String field
) {
    public String getCacheName() {
        return cacheName;
    }

    public String getField() {
        return field;
    }
}
