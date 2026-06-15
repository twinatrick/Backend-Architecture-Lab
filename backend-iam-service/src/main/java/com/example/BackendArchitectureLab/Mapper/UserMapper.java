package com.example.BackendArchitectureLab.Mapper;

import com.example.BackendArchitectureLab.Dto.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Dto.Vo.UserVo;
import com.example.BackendArchitectureLab.Entity.Function;
import com.example.BackendArchitectureLab.Entity.User;
import com.example.BackendArchitectureLab.Entity.UserRole;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = "spring", uses = {FunctionMapper.class})
public interface UserMapper {
    @Mapping(target = "id", expression = "java(user.getId() == null ? null : user.getId().toString())")
    UserVo toVo(User user);

    @Mapping(target = "id", expression = "java(userVo.getId() == null || userVo.getId().isBlank() ? null : java.util.UUID.fromString(userVo.getId()))")
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "userSkills", ignore = true)
    @Mapping(target = "userProjects", ignore = true)
    User toEntity(UserVo userVo);

    @AfterMapping
    default void fillPermissions(User user, @MappingTarget UserVo vo) {
        if (user == null) {
            return;
        }
        List<String> roleArr = user.getRoles().stream()
                .map(UserRole::getRole)
                .map(role -> role.getId().toString())
                .toList();
        vo.setRoleArr(roleArr);

        List<FunctionVo> permissions = new ArrayList<>();
        user.getRoles().forEach(userRole -> userRole.getRole().getRoleFunctions().forEach(
                roleFunction -> permissions.add(mapFunction(roleFunction.getFunction()))
        ));
        vo.setPermissions(permissions);
    }

    FunctionVo mapFunction(Function function);
}
