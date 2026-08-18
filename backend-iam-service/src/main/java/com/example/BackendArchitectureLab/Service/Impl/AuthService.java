package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Service.IAuthService;
import com.example.BackendArchitectureLab.Service.IRoleService;
import com.example.BackendArchitectureLab.Service.IUserService;
import com.example.BackendArchitectureLab.DataAccess.IFunctionDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserDataAccess;
import com.example.BackendArchitectureLab.Mapper.FunctionMapper;
import com.example.BackendArchitectureLab.Mapper.UserMapper;
import com.example.BackendArchitectureLab.Entity.Function;
import com.example.BackendArchitectureLab.Entity.User;
import com.example.BackendArchitectureLab.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Vo.RoleOutVo;
import com.example.BackendArchitectureLab.Vo.SignupRequest;
import com.example.BackendArchitectureLab.Vo.SuperUserRequest;
import com.example.BackendArchitectureLab.Vo.UserVo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService implements IAuthService {

    private final IUserDataAccess userDataAccess;
    private final IFunctionDataAccess functionDataAccess;
    private final IUserService userService;
    private final IRoleService roleService;
    private final UserMapper userMapper;
    private final FunctionMapper functionMapper;

    @Value("${superuser.key}")
    private String superUserKey;

    @Override
    @Transactional(readOnly = true)
    public List<FunctionVo> getAllParent(List<String> child) {
        List<UUID> childUUID = child.stream().map(UUID::fromString).toList();
        List<Function> functions = functionDataAccess.findAllById(childUUID);
        List<UUID> parentUUID = functions.stream()
                .map(Function::getParent)
                .filter(parent -> parent != null && !parent.isEmpty())
                .map(UUID::fromString)
                .toList();
        List<Function> parentFunctions = functionDataAccess.findAllById(parentUUID);

        List<String> result = new ArrayList<>(parentFunctions.stream().map(Function::getId).map(UUID::toString).toList());
        parentFunctions.stream().map(Function::getParent).forEach(result::add);
        List<Function> parentParentFunctions = functionDataAccess.findAllById(
                result.stream().filter(x -> x != null && !x.isEmpty()).map(UUID::fromString).toList()
        );

        return parentParentFunctions.stream().map(functionMapper::toVo).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserVo getCurrentUserInfo() {
        String email = getCurrentUserEmail();
        User user = userDataAccess.findByEmail(email).orElseThrow(
                () -> new IllegalArgumentException("User not found")
        );
        UserVo userVo = userMapper.toVo(user);
        List<FunctionVo> parent = getAllParent(userVo.getPermissions().stream().map(FunctionVo::getId).toList());
        userVo.getPermissions().addAll(parent);
        return userVo;
    }

    @Override
    @Transactional
    public UserVo signup(SignupRequest request) {
        List<UserVo> existingUsers = userService.getUserByEmail(request.getEmail());
        if (!existingUsers.isEmpty()) {
            throw new AppException("VALIDATION_ERROR", "User already exists", 400);
        }

        UserVo userVo = new UserVo();
        userVo.setEmail(request.getEmail());
        userVo.setPassword(request.getPassword());
        userVo.setName(request.getEmail());
        UserVo savedUser = userService.createUser(userVo);

        List<RoleOutVo> roles = roleService.getRole();
        RoleOutVo defaultRole = roles.stream()
                .filter(role -> "user".equalsIgnoreCase(role.getName()))
                .findFirst()
                .orElse(null);
        if (defaultRole != null) {
            roleService.userBindRole(String.valueOf(savedUser.getId()), List.of(String.valueOf(defaultRole.getId())));
        }
        return savedUser;
    }

    @Override
    @Transactional
    public UserVo createSuperUser(SuperUserRequest request) {
        if (request.getKey() == null || !request.getKey().equals(superUserKey)) {
            throw new AppException("VALIDATION_ERROR", "Invalid key", 400);
        }
        String email = (request.getEmail() == null || request.getEmail().isBlank()) ? "admin" : request.getEmail();
        List<UserVo> existingUsers = userService.getUserByEmail(email);
        if (!existingUsers.isEmpty()) {
            throw new AppException("VALIDATION_ERROR", "User already exists", 400);
        }

        UserVo userVo = new UserVo();
        userVo.setEmail(email);
        userVo.setPassword("admin");
        userVo.setName(email);
        UserVo savedUser = userService.createUser(userVo);

        RoleOutVo adminRole = roleService.getRoleByName("admin");
        if (adminRole == null) {
            RoleOutVo role = new RoleOutVo();
            role.setName("admin");
            adminRole = roleService.addRole(role);
        }
        roleService.userBindRole(String.valueOf(savedUser.getId()), List.of(String.valueOf(adminRole.getId())));
        return savedUser;
    }

    private String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalArgumentException("User not authenticated");
        }
        return auth.getName();
    }
}