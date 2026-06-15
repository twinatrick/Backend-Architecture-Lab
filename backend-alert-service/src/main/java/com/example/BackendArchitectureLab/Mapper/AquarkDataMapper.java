package com.example.BackendArchitectureLab.Mapper;

import com.example.BackendArchitectureLab.Dto.Vo.AquarkUse.AquarkDataRaw;
import com.example.BackendArchitectureLab.Entity.AquarkData;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface AquarkDataMapper {
    @Mapping(target = "id", expression = "java(mapId(aquarkData.getId()))")
    AquarkDataRaw toVo(AquarkData aquarkData);

    @Mapping(target = "id", expression = "java(mapUuid(aquarkDataRaw.getId()))")
    AquarkData toEntity(AquarkDataRaw aquarkDataRaw);

    default String mapId(UUID id) {
        return id == null ? null : id.toString();
    }

    default UUID mapUuid(String id) {
        return id == null || id.isBlank() ? null : UUID.fromString(id);
    }
}
