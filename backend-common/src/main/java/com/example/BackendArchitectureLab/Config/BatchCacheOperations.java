package com.example.BackendArchitectureLab.Config;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface BatchCacheOperations {

    BatchReadResult getAll(Collection<String> keys);

    void putAll(Map<String, ?> values);

    void putNulls(Collection<String> keys);

    record BatchReadResult(
            Map<String, Object> hits,
            Set<String> misses,
            Set<String> knownMissing
    ) {
    }
}
