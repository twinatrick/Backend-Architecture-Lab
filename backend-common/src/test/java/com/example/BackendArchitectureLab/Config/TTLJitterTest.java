package com.example.BackendArchitectureLab.Config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TTLJitterTest {

    private final RedisConfig redisConfig = new RedisConfig();

    TTLJitterTest() {
        redisConfig.setJitterMaxOffset(0.3);
    }

    @Test
    void jitter_MinimumValue_EqualsBase() {
        Duration base = Duration.ofMinutes(10);
        long min = Long.MAX_VALUE;
        for (int i = 0; i < 1000; i++) {
            Duration result = redisConfig.withJitter(base);
            min = Math.min(min, result.toMillis());
        }
        assertTrue(min >= base.toMillis(),
                "最小 TTL " + min + "ms 不應小於基礎 " + base.toMillis() + "ms");
    }

    @Test
    void jitter_MaximumValue_WithinRange() {
        Duration base = Duration.ofMinutes(10);
        long max = 0;
        long baseMs = base.toMillis();
        long expectedMax = baseMs + (long) (baseMs * 0.3);

        for (int i = 0; i < 1000; i++) {
            Duration result = redisConfig.withJitter(base);
            max = Math.max(max, result.toMillis());
        }

        assertTrue(max <= expectedMax + 1000,
                "最大 TTL " + max + "ms 不應超過 " + expectedMax + "ms（基礎 " + baseMs + "ms + 30%）");
        assertTrue(max > baseMs,
                "最大 TTL " + max + "ms 應大於基礎 " + baseMs + "ms（確認有亂數偏移）");
    }

    @Test
    void jitter_ProducesVariableResults() {
        Duration base = Duration.ofMinutes(10);
        Set<Long> uniqueValues = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            uniqueValues.add(redisConfig.withJitter(base).toMillis());
        }

        assertTrue(uniqueValues.size() > 1,
                "100 次取樣應產生多種不同 TTL 值，但只得到 " + uniqueValues.size() + " 種");
    }

    @Test
    void jitter_DifferentBaseDurations() {
        Duration[] bases = {
                Duration.ofMinutes(10),
                Duration.ofHours(1),
                Duration.ofHours(24)
        };

        for (Duration base : bases) {
            long baseMs = base.toMillis();
            long max = 0;
            long min = Long.MAX_VALUE;

            for (int i = 0; i < 500; i++) {
                Duration result = redisConfig.withJitter(base);
                max = Math.max(max, result.toMillis());
                min = Math.min(min, result.toMillis());
            }

            long expectedMax = baseMs + (long) (baseMs * 0.3);
            assertTrue(max <= expectedMax + 1000,
                    "基礎 " + base + "：最大值 " + max + "ms 超出上限 " + expectedMax + "ms");
            assertTrue(min >= baseMs,
                    "基礎 " + base + "：最小值 " + min + "ms 低於基礎 " + baseMs + "ms");
            assertTrue(max > baseMs,
                    "基礎 " + base + "：最大值 " + max + "ms 應大於基礎 " + baseMs + "ms（確認有偏移）");
        }
    }
}
