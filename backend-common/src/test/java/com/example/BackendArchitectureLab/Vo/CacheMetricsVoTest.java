package com.example.BackendArchitectureLab.Vo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CacheMetricsVoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fromRawStats_whenNullOrEmpty_shouldReturnDefaultZeroMetrics() {
        CacheMetricsVo fromNull = CacheMetricsVo.fromRawStats(null);
        assertNotNull(fromNull);
        assertEquals(0L, fromNull.hits());
        assertEquals(0L, fromNull.misses());
        assertEquals(0L, fromNull.bloomRejects());
        assertEquals(0L, fromNull.nullHits());
        assertEquals(0L, fromNull.total());
        assertEquals(0.0f, fromNull.hitRate());

        CacheMetricsVo fromEmpty = CacheMetricsVo.fromRawStats(Collections.emptyMap());
        assertNotNull(fromEmpty);
        assertEquals(0L, fromEmpty.hits());
        assertEquals(0L, fromEmpty.total());
        assertEquals(0.0f, fromEmpty.hitRate());
    }

    @Test
    void fromRawStats_withStandardKeys_shouldParseCorrectly() {
        Map<String, Object> raw = Map.of(
                "hits", 15L,
                "misses", 5L,
                "bloom_rejects", 2L,
                "null_hits", 3L
        );

        CacheMetricsVo vo = CacheMetricsVo.fromRawStats(raw);
        assertNotNull(vo);
        assertEquals(15L, vo.hits());
        assertEquals(5L, vo.misses());
        assertEquals(2L, vo.bloomRejects());
        assertEquals(3L, vo.nullHits());
        assertEquals(25L, vo.total());
        assertEquals(15f / 25f, vo.hitRate(), 0.0001f);
    }

    @Test
    void fromRawStats_withLegacyFallbackKeys_shouldParseCorrectly() {
        Map<String, Object> raw = Map.of(
                "hitCount", "10",
                "missCount", "10"
        );

        CacheMetricsVo vo = CacheMetricsVo.fromRawStats(raw);
        assertNotNull(vo);
        assertEquals(10L, vo.hits());
        assertEquals(10L, vo.misses());
        assertEquals(0L, vo.bloomRejects());
        assertEquals(0L, vo.nullHits());
        assertEquals(20L, vo.total());
        assertEquals(0.5f, vo.hitRate(), 0.0001f);
    }

    @Test
    void fromRawStats_withInvalidNumberFormat_shouldFallbackToZero() {
        Map<String, Object> raw = Map.of(
                "hits", "invalid-number",
                "misses", "2"
        );

        CacheMetricsVo vo = CacheMetricsVo.fromRawStats(raw);
        assertNotNull(vo);
        assertEquals(0L, vo.hits());
        assertEquals(2L, vo.misses());
        assertEquals(2L, vo.total());
        assertEquals(0.0f, vo.hitRate());
    }

    @Test
    void testSerializationAndDeserialization_RoundTrip() throws Exception {
        CacheMetricsVo original = new CacheMetricsVo(10L, 2L, 1L, 0L, 13L, 10f / 13f);

        String json = objectMapper.writeValueAsString(original);
        assertNotNull(json);
        assertTrue(json.contains("\"hits\":10"));
        assertTrue(json.contains("\"misses\":2"));

        CacheMetricsVo deserialized = objectMapper.readValue(json, CacheMetricsVo.class);
        assertEquals(original, deserialized);
        assertEquals(10L, deserialized.hits());
        assertEquals(2L, deserialized.misses());
        assertEquals(13L, deserialized.total());
    }
}
