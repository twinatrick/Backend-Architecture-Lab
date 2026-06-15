package com.example.BackendArchitectureLab.Util;

import com.example.BackendArchitectureLab.Dto.Vo.SkillLevelBindingItem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SkillLevelBindingMapper {

    private SkillLevelBindingMapper() {
    }

    public static Map<UUID, UUID> toSkillLevelMap(List<SkillLevelBindingItem> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return Map.of();
        }

        Map<UUID, UUID> map = new LinkedHashMap<>();
        for (SkillLevelBindingItem item : bindings) {
            if (item == null || item.getSkillId() == null || item.getSkillLevelId() == null) {
                throw new IllegalArgumentException("Key must not be null");
            }
            map.put(UUID.fromString(item.getSkillId()), UUID.fromString(item.getSkillLevelId()));
        }
        return map;
    }
}
