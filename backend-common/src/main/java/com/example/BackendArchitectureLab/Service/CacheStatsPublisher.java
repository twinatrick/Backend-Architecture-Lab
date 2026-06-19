package com.example.BackendArchitectureLab.Service;

public interface CacheStatsPublisher {
    void publish(String cacheName, String field);
}
