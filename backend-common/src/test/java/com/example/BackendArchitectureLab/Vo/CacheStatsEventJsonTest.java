package com.example.BackendArchitectureLab.Vo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CacheStatsEventJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testSerializationAndDeserialization_RoundTrip() throws Exception {
        CacheStatsEvent original = new CacheStatsEvent("roles", "hit");

        String json = objectMapper.writeValueAsString(original);
        assertNotNull(json);
        assertTrue(json.contains("roles"));
        assertTrue(json.contains("hit"));

        CacheStatsEvent deserialized = objectMapper.readValue(json, CacheStatsEvent.class);
        assertEquals(original, deserialized);
        assertEquals("roles", deserialized.cacheName());
        assertEquals("hit", deserialized.field());
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
    }
}
