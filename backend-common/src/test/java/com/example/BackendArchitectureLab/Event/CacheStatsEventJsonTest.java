package com.example.BackendArchitectureLab.Event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CacheStatsEventJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testSerializationAndDeserialization_RoundTrip() throws Exception {
        CacheStatsEvent original = new CacheStatsEvent("roles", "hit", 1);

        String json = objectMapper.writeValueAsString(original);
        assertNotNull(json);
        assertTrue(json.contains("roles"));
        assertTrue(json.contains("hit"));

        CacheStatsEvent deserialized = objectMapper.readValue(json, CacheStatsEvent.class);
        assertEquals(original, deserialized);
        assertEquals("roles", deserialized.cacheName());
        assertEquals("hit", deserialized.field());
        assertEquals(1, deserialized.count());
    }

    @Test
    void testDeserialization_FromRawJsonString() throws Exception {
        String json = """
                {
                    "cacheName": "projects",
                    "field": "miss"
                }
                """;

        CacheStatsEvent event = objectMapper.readValue(json, CacheStatsEvent.class);
        assertNotNull(event);
        assertEquals("projects", event.cacheName());
        assertEquals("miss", event.field());
        assertEquals(0, event.count(), "舊事件缺少 count 時應使用 primitive 預設值，Consumer 會相容轉為 1");
    }
}
