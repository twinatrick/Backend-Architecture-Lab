package com.example.BackendArchitectureLab.Mapper;

import com.example.BackendArchitectureLab.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Entity.Function;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface FunctionMapper {
    @Mapping(target = "id", expression = "java(mapId(function.getId()))")
    @Mapping(target = "parentName", ignore = true)
    @Mapping(target = "grandParentId", ignore = true)
    @Mapping(target = "disabled", ignore = true)
    @Mapping(target = "edit", ignore = true)
    @Mapping(target = "newAdd", ignore = true)
    @Mapping(target = "newName", ignore = true)
    @Mapping(target = "delete", ignore = true)
    FunctionVo toVo(Function function);

    @Mapping(target = "id", expression = "java(mapUuid(functionVo.getId()))")
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "createdTime", ignore = true)
    @Mapping(target = "updatedTime", ignore = true)
    @Mapping(target = "roleFunctions", ignore = true)
    Function toEntity(FunctionVo functionVo);

    default String mapId(UUID id) {
        return id == null ? null : id.toString();
    }

    default UUID mapUuid(String id) {
        return id == null || id.isBlank() ? null : UUID.fromString(id);
    }

    @AfterMapping
    default void fillDerived(Function function, @MappingTarget FunctionVo vo) {
        if (function == null) {
            return;
        }
        vo.setParentName(function.getParent());
        vo.setGrandParentId("");
        vo.setDisabled(false);
        vo.setEdit(false);
        vo.setNewAdd(false);
        vo.setNewName(function.getName());
        vo.setDelete(false);
    }
}
