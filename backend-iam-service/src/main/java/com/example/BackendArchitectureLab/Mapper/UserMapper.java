package com.example.BackendArchitectureLab.Mapper;

import com.example.BackendArchitectureLab.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Vo.UserVo;
import com.example.BackendArchitectureLab.Entity.Function;
import com.example.BackendArchitectureLab.Entity.Role;
import com.example.BackendArchitectureLab.Entity.User;
import com.example.BackendArchitectureLab.Entity.UserRole;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring", uses = {FunctionMapper.class})
public interface UserMapper {
    @Named("toVo")
    @BeanMapping(qualifiedByName = "fillStreamRolesOnly")
    @Mapping(target = "id", expression = "java(user.getId() == null ? null : user.getId().toString())")
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "roleNames", ignore = true)
    @Mapping(target = "permissions", ignore = true)
    UserVo toVo(User user);

    @Named("toVoWithPermissions")
    @BeanMapping(qualifiedByName = "fillPermissions")
    @Mapping(target = "id", expression = "java(user.getId() == null ? null : user.getId().toString())")
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "roleNames", ignore = true)
    UserVo toVoWithPermissions(User user);

    @Named("toStreamVo")
    @BeanMapping(qualifiedByName = "fillStreamRolesOnly")
    @Mapping(target = "id", expression = "java(user.getId() == null ? null : user.getId().toString())")
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "roleNames", ignore = true)
    @Mapping(target = "permissions", ignore = true)
    UserVo toStreamVo(User user);

    @Mapping(target = "id", expression = "java(userVo.getId() == null || userVo.getId().isBlank() ? null : java.util.UUID.fromString(userVo.getId()))")
    @Mapping(target = "roles", ignore = true)
    User toEntity(UserVo userVo);

    /**
     * CAUTION: This method accesses LAZY-loaded collections (user.getRoles(), role.getRoleFunctions()).
     * It MUST be called within an active @Transactional context.
     * If called on a detached entity outside a transaction, LazyInitializationException will be thrown.
     */
    @Named("fillPermissions")
    @AfterMapping
    default void fillPermissions(User user, @MappingTarget UserVo vo) {
        if (user == null) {
            return;
        }
        fillRoles(user, vo);

        List<FunctionVo> permissions = new ArrayList<>();
        if (user.getRoles() != null) {
            user.getRoles().forEach(userRole -> {
                if (userRole.getRole() != null && userRole.getRole().getRoleFunctions() != null) {
                    userRole.getRole().getRoleFunctions().forEach(
                            roleFunction -> permissions.add(mapFunction(roleFunction.getFunction()))
                    );
                }
            });
        }
        vo.setPermissions(permissions);
    }

    @Named("fillStreamRolesOnly")
    @AfterMapping
    default void fillStreamRolesOnly(User user, @MappingTarget UserVo vo) {
        if (user == null) {
            return;
        }
        fillRoles(user, vo);
        vo.setPermissions(Collections.emptyList());
    }

    default void fillRoles(User user, UserVo vo) {
        if (user.getRoles() == null) {
            return;
        }
        List<String> roleIds = user.getRoles().stream()
                .map(UserRole::getRole)
                .filter(role -> role != null && role.getId() != null)
                .map(role -> role.getId().toString())
                .toList();
        vo.setRoles(roleIds);

        List<String> roleNames = user.getRoles().stream()
                .map(UserRole::getRole)
                .filter(role -> role != null && role.getName() != null)
                .map(Role::getName)
                .toList();
        vo.setRoleNames(roleNames);
    }

    FunctionVo mapFunction(Function function);
}
