package com.example.BackendArchitectureLab.Vo;

/**
 * CacheStatsEvent - 快取命中與指標統計事件。
 * <p>
 * 註：保留 {@code getCacheName()}、{@code getField()} 係為確保與
 * 既有 JavaBean 反射規範、舊版序列化工具及 Spring EL 表達式完全相容；
 * 內部業務邏輯中建議優先使用標準 Record Component Accessors（{@link #cacheName()} 等）。
 */
public record CacheStatsEvent(
        String cacheName,
        String field
) {
    /**
     * 相容 JavaBean 命名慣例之 Getter。
     */
    public String getCacheName() {
        return cacheName;
    }

    /**
     * 相容 JavaBean 命名慣例之 Getter。
     */
    public String getField() {
        return field;
    }
}
