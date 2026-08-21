package com.example.BackendArchitectureLab.Vo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BindingSnapshotJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testSerializationAndDeserialization_RoundTrip() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID levelId = UUID.randomUUID();

        BindingSnapshot original = new BindingSnapshot(userId, skillId, levelId);

        String json = objectMapper.writeValueAsString(original);
        assertNotNull(json);
        assertTrue(json.contains(userId.toString()));
        assertTrue(json.contains(skillId.toString()));
        assertTrue(json.contains(levelId.toString()));

        BindingSnapshot deserialized = objectMapper.readValue(json, BindingSnapshot.class);
        assertEquals(original, deserialized);
        assertEquals(userId, deserialized.userId());
        assertEquals(skillId, deserialized.skillId());
        assertEquals(levelId, deserialized.levelId());
    }

    @Test
    void testDeserialization_FromRawJsonString() throws Exception {
        String json = """
                {
                    "userId": "11111111-1111-1111-1111-111111111111",
                    "skillId": "22222222-2222-2222-2222-222222222222",
                    "levelId": "33333333-3333-3333-3333-333333333333"
                }
                """;

        BindingSnapshot snapshot = objectMapper.readValue(json, BindingSnapshot.class);
        assertNotNull(snapshot);
        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), snapshot.userId());
        assertEquals(UUID.fromString("22222222-2222-2222-2222-222222222222"), snapshot.skillId());
        assertEquals(UUID.fromString("33333333-3333-3333-3333-333333333333"), snapshot.levelId());
    }
}
