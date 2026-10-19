package com.example.BackendArchitectureLab.Mapper;

import com.example.BackendArchitectureLab.Vo.RoleVo;
import com.example.BackendArchitectureLab.Entity.BaseEntity;
import com.example.BackendArchitectureLab.Entity.Role;
import com.example.BackendArchitectureLab.Entity.RoleFunction;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring", uses = {FunctionMapper.class})
public interface RoleMapper {
    @Named("toVo")
    @BeanMapping(qualifiedByName = "fillFunctionIds")
    @Mapping(target = "functionIds", ignore = true)
    RoleVo toVo(Role role);

    @Named("toSearchVo")
    @BeanMapping(qualifiedByName = "emptyFunctionIds")
    @Mapping(target = "functionIds", ignore = true)
    RoleVo toSearchVo(Role role);

    @Mapping(target = "userRoles", ignore = true)
    @Mapping(target = "roleFunctions", ignore = true)
    Role toEntity(RoleVo roleOutVo);

    /**
     * CAUTION: This method accesses LAZY-loaded collections (role.getRoleFunctions()).
     * It MUST be called within an active @Transactional context.
     * If called on a detached entity outside a transaction, LazyInitializationException will be thrown.
     */
    @Named("fillFunctionIds")
    @AfterMapping
    default void fillFunctionIds(Role role, @MappingTarget RoleVo vo) {
        if (role == null) {
            return;
        }
        List<String> ids = role.getRoleFunctions().stream()
                .map(RoleFunction::getFunction)
                .map(BaseEntity::getId)
                .map(UUID::toString)
                .toList();
        vo.setFunctionIds(ids);
        vo.setFunctionCount(ids.size());
    }

    @Named("emptyFunctionIds")
    @AfterMapping
    default void emptyFunctionIds(Role role, @MappingTarget RoleVo vo) {
        if (role == null) {
            return;
        }
        vo.setFunctionIds(Collections.emptyList());
        vo.setFunctionCount(role.getRoleFunctions() != null ? role.getRoleFunctions().size() : 0);
    }
}
