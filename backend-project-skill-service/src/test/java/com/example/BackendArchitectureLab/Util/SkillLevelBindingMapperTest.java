package com.example.BackendArchitectureLab.Util;

import com.example.BackendArchitectureLab.Dto.Vo.SkillLevelBindingItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SkillLevelBindingMapperTest {

    @Test
    @DisplayName("Should convert bindings to map successfully")
    void testToSkillLevelMap_Success() {
        SkillLevelBindingItem item1 = new SkillLevelBindingItem();
        item1.setSkillId(UUID.randomUUID().toString());
        item1.setSkillLevelId(UUID.randomUUID().toString());
        SkillLevelBindingItem item2 = new SkillLevelBindingItem();
        item2.setSkillId(UUID.randomUUID().toString());
        item2.setSkillLevelId(UUID.randomUUID().toString());
        List<SkillLevelBindingItem> bindings = List.of(item1, item2);

        Map<UUID, UUID> result = SkillLevelBindingMapper.toSkillLevelMap(bindings);

        assertEquals(2, result.size());
        assertEquals(UUID.fromString(item1.getSkillLevelId()), result.get(UUID.fromString(item1.getSkillId())));
        assertEquals(UUID.fromString(item2.getSkillLevelId()), result.get(UUID.fromString(item2.getSkillId())));
    }

    @Test
    @DisplayName("Should return empty map when bindings is null")
    void testToSkillLevelMap_NullList() {
        Map<UUID, UUID> result = SkillLevelBindingMapper.toSkillLevelMap(null);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should return empty map when bindings is empty")
    void testToSkillLevelMap_EmptyList() {
        Map<UUID, UUID> result = SkillLevelBindingMapper.toSkillLevelMap(List.of());

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should throw exception when binding item is null")
    void testToSkillLevelMap_NullItem() {
        List<SkillLevelBindingItem> bindings = new java.util.ArrayList<>();
        bindings.add(null);

        assertThrows(IllegalArgumentException.class,
                () -> SkillLevelBindingMapper.toSkillLevelMap(bindings));
    }

    @Test
    @DisplayName("Should throw exception when skillId is null")
    void testToSkillLevelMap_NullSkillId() {
        SkillLevelBindingItem item = new SkillLevelBindingItem();
        item.setSkillId(null);
        item.setSkillLevelId(UUID.randomUUID().toString());

        assertThrows(IllegalArgumentException.class,
                () -> SkillLevelBindingMapper.toSkillLevelMap(List.of(item)));
    }

    @Test
    @DisplayName("Should throw exception when skillLevelId is null")
    void testToSkillLevelMap_NullSkillLevelId() {
        SkillLevelBindingItem item = new SkillLevelBindingItem();
        item.setSkillId(UUID.randomUUID().toString());
        item.setSkillLevelId(null);

        assertThrows(IllegalArgumentException.class,
                () -> SkillLevelBindingMapper.toSkillLevelMap(List.of(item)));
    }

    @Test
    @DisplayName("Should preserve insertion order")
    void testToSkillLevelMap_Order() {
        SkillLevelBindingItem item1 = new SkillLevelBindingItem();
        item1.setSkillId("00000000-0000-0000-0000-000000000001");
        item1.setSkillLevelId("00000000-0000-0000-0000-000000000010");
        SkillLevelBindingItem item2 = new SkillLevelBindingItem();
        item2.setSkillId("00000000-0000-0000-0000-000000000002");
        item2.setSkillLevelId("00000000-0000-0000-0000-000000000020");
        List<SkillLevelBindingItem> bindings = List.of(item1, item2);

        Map<UUID, UUID> result = SkillLevelBindingMapper.toSkillLevelMap(bindings);

        UUID[] keys = result.keySet().toArray(new UUID[0]);
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000001"), keys[0]);
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000002"), keys[1]);
    }
}
