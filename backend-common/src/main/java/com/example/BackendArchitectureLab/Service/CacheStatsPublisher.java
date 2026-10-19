package com.example.BackendArchitectureLab.Service;

public interface CacheStatsPublisher {
    void publish(String cacheName, String field);

    default void publish(String cacheName, String field, long count) {
        for (long index = 0; index < count; index++) {
            publish(cacheName, field);
        }
    }
}
