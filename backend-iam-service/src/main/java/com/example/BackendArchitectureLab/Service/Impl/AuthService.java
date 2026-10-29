package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Service.IAuthService;
import com.example.BackendArchitectureLab.Service.IRoleService;
import com.example.BackendArchitectureLab.Service.IUserService;
import com.example.BackendArchitectureLab.DataAccess.IFunctionDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IRoleDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserDataAccess;
import com.example.BackendArchitectureLab.Mapper.FunctionMapper;
import com.example.BackendArchitectureLab.Mapper.UserMapper;
import com.example.BackendArchitectureLab.Entity.Function;
import com.example.BackendArchitectureLab.Entity.Role;
import com.example.BackendArchitectureLab.Entity.User;
import com.example.BackendArchitectureLab.Entity.UserRole;
import com.example.BackendArchitectureLab.Util.CacheExecutor;
import com.example.BackendArchitectureLab.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Vo.RoleVo;
import com.example.BackendArchitectureLab.Dto.Request.SignupRequest;
import com.example.BackendArchitectureLab.Dto.Request.SuperUserRequest;
import com.example.BackendArchitectureLab.Vo.UserVo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService implements IAuthService {

    private final IUserDataAccess userDataAccess;
    private final IFunctionDataAccess functionDataAccess;
    private final IRoleDataAccess roleDataAccess;
    private final IUserService userService;
    private final IRoleService roleService;
    private final UserMapper userMapper;
    private final FunctionMapper functionMapper;
    private final CacheExecutor cacheExecutor;

    @Value("${superuser.key}")
    private String superUserKey;

    @Override
    @Transactional(readOnly = true)
    public List<FunctionVo> getAllParent(List<String> child) {
        return loadAllParent(child);
    }

    @Override
    @Transactional(readOnly = true)
    public UserVo getCurrentUserInfo() {
        String email = getCurrentUserEmail();
        return loadUserInfoByEmail(email);
    }

    @Override
    @Transactional(readOnly = true)
    public UserVo getUserInfoByEmail(String email) {
        return loadUserInfoByEmail(email);
    }

    private UserVo loadUserInfoByEmail(String email) {
        Supplier<UserVo> loader = () -> {
            User user = userDataAccess.findByEmailWithRoles(email).orElseThrow(
                    () -> new IllegalArgumentException("User not found")
            );
            enrichUserRoleFunctions(user);
            UserVo userVo = userMapper.toVoWithPermissions(user);
            List<FunctionVo> parent = loadAllParent(userVo.getPermissions().stream().map(FunctionVo::getId).toList());
            userVo.getPermissions().addAll(parent);
            return userVo;
        };
        if (cacheExecutor == null) {
            return loader.get();
        }
        return cacheExecutor.executeCacheable("userInfo", email, loader);
    }

    private List<FunctionVo> loadAllParent(List<String> child) {
        if (child == null || child.isEmpty()) {
            return List.of();
        }
        List<UUID> childUUIDs = child.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(UUID::fromString)
                .toList();
        if (childUUIDs.isEmpty()) {
            return List.of();
        }
        List<Function> parentFunctions = functionDataAccess.findAllParentHierarchyByChildIds(childUUIDs);
        return parentFunctions.stream().map(functionMapper::toVo).toList();
    }

    private void enrichUserRoleFunctions(User user) {
        if (roleDataAccess == null || user.getRoles() == null || user.getRoles().isEmpty()) {
            return;
        }
        List<UUID> roleIds = user.getRoles().stream()
                .map(UserRole::getRole)
                .filter(Objects::nonNull)
                .map(Role::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (roleIds.isEmpty()) {
            return;
        }
        List<Role> rolesWithFunctions = roleDataAccess.findAllByIdsWithFunctions(roleIds);
        if (rolesWithFunctions == null || rolesWithFunctions.isEmpty()) {
            return;
        }
        Map<UUID, Role> roleMap = rolesWithFunctions.stream()
                .filter(r -> r.getId() != null)
                .collect(Collectors.toMap(Role::getId, r -> r, (first, second) -> first));
        for (UserRole userRole : user.getRoles()) {
            if (userRole.getRole() != null && userRole.getRole().getId() != null) {
                Role enrichedRole = roleMap.get(userRole.getRole().getId());
                if (enrichedRole != null) {
                    userRole.setRole(enrichedRole);
                }
            }
        }
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

        List<RoleVo> roles = roleService.getRole();
        RoleVo defaultRole = roles.stream()
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
        if (request.getBootstrapSecret() == null || !request.getBootstrapSecret().equals(superUserKey)) {
            throw new AppException("VALIDATION_ERROR", "Invalid bootstrap secret", 400);
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

        RoleVo adminRole = roleService.getRoleByName("admin");
        if (adminRole == null) {
            RoleVo role = new RoleVo();
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