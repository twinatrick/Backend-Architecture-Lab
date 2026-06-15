package com.example.BackendArchitectureLab.Service;

import java.util.Collection;

public interface IBloomFilterService {

    boolean mightContain(String cacheName, String key);

    void add(String cacheName, String key);

    void addAll(String cacheName, Collection<String> keys);

    long count(String cacheName);

    void clear(String cacheName);
}
