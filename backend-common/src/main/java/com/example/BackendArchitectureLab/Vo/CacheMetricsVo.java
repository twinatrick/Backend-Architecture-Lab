package com.example.BackendArchitectureLab.Vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.Map;

@Schema(description = "快取度量統計資料")
public record CacheMetricsVo(
        @Schema(description = "快取命中次數", example = "10")
        Long hits,

        @Schema(description = "快取未命中次數", example = "2")
        Long misses,

        @Schema(description = "布隆過濾器攔截次數", example = "0")
        Long bloomRejects,

        @Schema(description = "空值防護命中次數", example = "0")
        Long nullHits,

        @Schema(description = "總請求次數 (hits + misses + bloomRejects + nullHits)", example = "12")
        Long total,

        @Schema(description = "快取命中率 (0.0 ~ 1.0)", example = "0.8333")
        Float hitRate
) implements Serializable {

    public static CacheMetricsVo fromRawStats(Map<?, ?> rawStats) {
        if (rawStats == null || rawStats.isEmpty()) {
            return new CacheMetricsVo(0L, 0L, 0L, 0L, 0L, 0.0f);
        }
        long hits = parseMetric(rawStats, "hits", "hitCount");
        long misses = parseMetric(rawStats, "misses", "missCount");
        long bloomRejects = parseMetric(rawStats, "bloom_rejects", "bloomRejects");
        long nullHits = parseMetric(rawStats, "null_hits", "nullHits");

        long total = hits + misses + bloomRejects + nullHits;
        float hitRate = (total > 0L) ? ((float) hits / (float) total) : 0.0f;

        return new CacheMetricsVo(hits, misses, bloomRejects, nullHits, total, hitRate);
    }

    private static long parseMetric(Map<?, ?> stats, String primaryKey, String fallbackKey) {
        Object val = stats.get(primaryKey);
        if (val == null && fallbackKey != null) {
            val = stats.get(fallbackKey);
        }
        if (val == null) {
            return 0L;
        }
        try {
            if (val instanceof Number number) {
                return number.longValue();
            }
            return Long.parseLong(val.toString().trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
