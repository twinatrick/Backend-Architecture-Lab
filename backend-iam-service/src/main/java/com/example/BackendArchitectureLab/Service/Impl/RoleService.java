package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Dto.Query.RoleSearchQuery;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Service.IRoleService;
import com.example.BackendArchitectureLab.Util.BatchCacheHelper;
import com.example.BackendArchitectureLab.Util.CacheExecutor;
import com.example.BackendArchitectureLab.Util.IamLocalCacheManager;
import com.example.BackendArchitectureLab.Vo.BatchResultVo;
import com.example.BackendArchitectureLab.Util.SearchSortPolicy;
import com.example.BackendArchitectureLab.DataAccess.*;
import com.example.BackendArchitectureLab.Mapper.FunctionMapper;
import com.example.BackendArchitectureLab.Mapper.RoleMapper;
import com.example.BackendArchitectureLab.Mapper.UserMapper;
import com.example.BackendArchitectureLab.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Vo.RoleVo;
import com.example.BackendArchitectureLab.Vo.UserVo;
import com.example.BackendArchitectureLab.Entity.Function;
import com.example.BackendArchitectureLab.Entity.Role;
import com.example.BackendArchitectureLab.Entity.RoleFunction;
import com.example.BackendArchitectureLab.Entity.User;
import com.example.BackendArchitectureLab.Entity.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.example.BackendArchitectureLab.Util.TransactionExecutor;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService implements IRoleService {
    private static final SearchSortPolicy SEARCH_SORT_POLICY = new SearchSortPolicy(
            "id", "name", "description", "permissions",
            "createdBy", "updatedBy", "createdTime", "updatedTime"
    );
    private static final Set<String> SYSTEM_RESERVED_ROLES = Set.of("admin", "user");
    private static final String DEFAULT_ADMIN_EMAIL = "admin";
    private static final String ADMIN_ROLE_NAME = "admin";

    private final TransactionExecutor transactionExecutor;
    private final IRoleDataAccess roleDataAccess;
    private final IRoleFunctionDataAccess roleFunctionDataAccess;
    private final IFunctionDataAccess functionDataAccess;
    private final IUserDataAccess userDataAccess;
    private final IUserRoleDataAccess userRoleDataAccess;
    private final RoleMapper roleMapper;
    private final FunctionMapper functionMapper;
    private final UserMapper userMapper;
    private final CacheManager cacheManager;
    private final CacheExecutor cacheExecutor;
    private final IamLocalCacheManager iamLocalCacheManager;

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "roles", key = "'all'"),
        @CacheEvict(value = "roles", key = "'byname:' + #roleOutVo.name")
    })
    public RoleVo addRole(RoleVo roleOutVo) {
        return doAddRole(roleOutVo);
    }

    private RoleVo doAddRole(RoleVo roleOutVo) {
        Role role = roleMapper.toEntity(roleOutVo);
        Role exampleRole = new Role();
        exampleRole.setName(role.getName());
        Example<Role> example = Example.of(exampleRole);
        if (role.getId() != null) {
            throw new IllegalArgumentException("Key must be null");
        } else if (role.getName() == null) {
            throw new IllegalArgumentException("Name must not be null");
        } else if (roleDataAccess.exists(example)) {
            throw new IllegalArgumentException("Name already exists");
        }

        return roleMapper.toVo(roleDataAccess.save(role));
    }

    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "roles", key = "'all'"),
        @CacheEvict(value = "roles", key = "'byname:' + #roleOutVo.name"),
        @CacheEvict(value = "roles", key = "#roleOutVo.id", condition = "#roleOutVo.id != null")
    })
    public RoleVo addRoleWithFunctions(RoleVo roleOutVo) {
        RoleVo savedRole = doAddRole(roleOutVo);
        syncRoleFunctions(savedRole.getId(), roleOutVo.getFunctionIds());
        evictCache("roles", savedRole.getId().toString());
        return getRoleById(savedRole.getId().toString());
    }

    @Override
    public List<RoleVo> getRole() {
        Supplier<List<RoleVo>> loader = () -> transactionExecutor.executeReadOnly(() -> {
            List<Role> roles = enrichRolesWithFunctions(roleDataAccess.findAll());
            return roles.stream().map(roleMapper::toVo).toList();
        });
        if (cacheExecutor == null) {
            return loader.get();
        }
        return cacheExecutor.executeCacheableList("roles", "all", loader);
    }

    @Override
    public RoleVo getRoleById(String roleId) {
        Supplier<RoleVo> loader = () -> transactionExecutor.executeReadOnly(() -> {
            UUID roleUuid = mapUuid(roleId);
            if (roleUuid == null) {
                throw new IllegalArgumentException("Key must not be null");
            }
            Role role = roleDataAccess.findByIdWithRoleFunctions(roleUuid)
                    .or(() -> roleDataAccess.findById(roleUuid))
                    .orElseThrow(() -> new IllegalArgumentException("Role not found"));
            return roleMapper.toVo(role);
        });
        if (cacheExecutor == null) {
            return loader.get();
        }
        return cacheExecutor.executeCacheable("roles", roleId, loader);
    }

    @Override
    public BatchResultVo<RoleVo> getRolesByIds(List<String> ids) {
        return BatchCacheHelper.fetchBatchWithCache(
                cacheManager,
                "roles",
                ids,
                missingIds -> transactionExecutor.executeReadOnly(() -> {
                    List<UUID> missingUuids = missingIds.stream()
                            .map(this::mapUuid)
                            .filter(Objects::nonNull)
                            .toList();
                    if (missingUuids.isEmpty()) {
                        return List.of();
                    }
                    List<Role> roles = roleDataAccess.findAllByIdsWithFunctions(missingUuids);
                    if (roles == null || roles.isEmpty()) {
                        roles = roleDataAccess.findAllById(missingUuids);
                    }
                    List<RoleVo> list = new ArrayList<>();
                    for (Role role : roles) {
                        list.add(roleMapper.toVo(role));
                    }
                    return list;
                }),
                vo -> vo.getId() != null ? vo.getId().toString() : null
        );
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "roles", key = "#roleOutVo.id"),
        @CacheEvict(value = "roles", key = "'byname:' + #roleOutVo.name"),
        @CacheEvict(value = "roles", key = "'all'"),
        @CacheEvict(value = "userPermissionsVo", allEntries = true),
        @CacheEvict(value = "userInfo", allEntries = true)
    })
    public RoleVo updateRole(RoleVo roleOutVo) {
        return doUpdateRole(roleOutVo);
    }

    private RoleVo doUpdateRole(RoleVo roleOutVo) {
        Role role = roleMapper.toEntity(roleOutVo);
        if (role.getId() == null) {
            throw new IllegalArgumentException("Key must not be null");
        } else if (role.getName() == null) {
            throw new IllegalArgumentException("Name must not be null");
        }
        Role existing = roleDataAccess.findById(role.getId()).orElseThrow(
                () -> new IllegalArgumentException("Role not found")
        );
        String oldName = existing.getName();
        if (isSystemReservedRole(oldName) && !oldName.equalsIgnoreCase(role.getName().trim())) {
            throw new AppException("FORBIDDEN_SYSTEM_ROLE", "系統核心角色不可變更名稱", 400);
        }
        existing.setName(role.getName());
        existing.setDescription(role.getDescription());
        RoleVo result = roleMapper.toVo(roleDataAccess.save(existing));
        if (oldName != null) {
            evictCache("roles", "byname:" + oldName);
        }
        evictAllLocalCaches();
        return result;
    }

    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "roles", key = "#roleOutVo.id"),
        @CacheEvict(value = "roles", key = "'byname:' + #roleOutVo.name"),
        @CacheEvict(value = "roles", key = "'all'"),
        @CacheEvict(value = "roleFunctions", key = "#roleOutVo.id"),
        @CacheEvict(value = "userPermissionsVo", allEntries = true),
        @CacheEvict(value = "userInfo", allEntries = true)
    })
    public RoleVo updateRoleWithFunctions(RoleVo roleOutVo) {
        RoleVo updatedRole = doUpdateRole(roleOutVo);
        syncRoleFunctions(updatedRole.getId(), roleOutVo.getFunctionIds());
        evictCache("roles", updatedRole.getId().toString());
        evictCache("roles", "all");
        if (roleOutVo.getName() != null) {
            evictCache("roles", "byname:" + roleOutVo.getName());
        }
        clearCache("userPermissionsVo");
        clearCache("userInfo");
        evictAllLocalCaches();
        return getRoleById(updatedRole.getId().toString());
    }

    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "roles", key = "#roleOutVo.id"),
        @CacheEvict(value = "roles", key = "'byname:' + #roleOutVo.name"),
        @CacheEvict(value = "roles", key = "'all'"),
        @CacheEvict(value = "roleFunctions", key = "#roleOutVo.id"),
        @CacheEvict(value = "userRoles", allEntries = true),
        @CacheEvict(value = "users", allEntries = true),
        @CacheEvict(value = "userPermissionsVo", allEntries = true),
        @CacheEvict(value = "userInfo", allEntries = true)
    })
    public void deleteRole(RoleVo roleOutVo) {
        Role role = roleMapper.toEntity(roleOutVo);
        if (role.getId() == null) {
            throw new IllegalArgumentException("Key must not be null");
        }
        role = roleDataAccess.findById(role.getId()).orElseThrow(
                () -> new IllegalArgumentException("Role not found")
        );
        if (isSystemReservedRole(role.getName())) {
            throw new AppException("FORBIDDEN_SYSTEM_ROLE", "系統核心角色不可刪除", 400);
        }
        String deletedRoleName = role.getName();
        UUID deletedRoleId = role.getId();
        roleFunctionDataAccess.deleteByRoleKey(deletedRoleId);
        userRoleDataAccess.deleteByRoleId(deletedRoleId);
        roleDataAccess.delete(role);

        if (deletedRoleName != null) {
            evictCache("roles", "byname:" + deletedRoleName);
        }
        evictCache("userRoles", "byrole:" + deletedRoleId);
        clearCache("users");
        clearCache("userPermissionsVo");
        clearCache("userInfo");
        evictAllLocalCaches();
    }
    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "roles", key = "#roleId"),
        @CacheEvict(value = "roles", key = "'all'"),
        @CacheEvict(value = "roleFunctions", key = "#roleId"),
        @CacheEvict(value = "userPermissionsVo", allEntries = true),
        @CacheEvict(value = "userInfo", allEntries = true)
    })
    public void roleBindFunction(String roleId, List<String> functionIds) {
        UUID roleUuid = mapUuid(roleId);
        if (roleUuid == null) {
            throw new IllegalArgumentException("Key must not be null");
        } else if (functionIds == null || functionIds.isEmpty()) {
            throw new IllegalArgumentException("Function list is empty");
        }
        Role role = roleDataAccess.findById(roleUuid).orElseThrow(
                () -> new IllegalArgumentException("Role not found")
        );
        List<UUID> functionUuids = functionIds.stream().map(UUID::fromString).toList();
        List<Function> functions = functionDataAccess.findAllById(functionUuids);
        roleFunctionDataAccess.deleteByFunctionAndRole(functions, List.of(role));
        List<RoleFunction> roleFunctions = functions.stream().map(function -> {
            RoleFunction roleFunction = new RoleFunction();
            roleFunction.setRole(role);
            roleFunction.setFunction(function);
            return roleFunction;
        }).toList();
        roleFunctionDataAccess.saveAll(roleFunctions);
        evictAllLocalCaches();
    }

    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "roles", allEntries = true),
        @CacheEvict(value = "roleFunctions", allEntries = true),
        @CacheEvict(value = "userPermissionsVo", allEntries = true),
        @CacheEvict(value = "userInfo", allEntries = true)
    })
    public void functionBindRole(String functionId, List<String> roleIds) {
        UUID functionUuid = mapUuid(functionId);
        if (functionUuid == null) {
            throw new IllegalArgumentException("Key must not be null");
        } else if (roleIds == null || roleIds.isEmpty()) {
            throw new IllegalArgumentException("Role list is empty");
        }
        Function function = functionDataAccess.findById(functionUuid).orElseThrow(
                () -> new IllegalArgumentException("Function not found")
        );
        List<UUID> roleUuids = roleIds.stream().map(UUID::fromString).toList();
        List<Role> roles = roleDataAccess.findAllById(roleUuids);
        roleFunctionDataAccess.deleteByFunctionAndRole(List.of(function), roles);

        List<RoleFunction> roleFunctions = roles.stream().map(role -> {
            RoleFunction roleFunction = new RoleFunction();
            roleFunction.setRole(role);
            roleFunction.setFunction(function);
            return roleFunction;
        }).toList();
        roleFunctionDataAccess.saveAll(roleFunctions);

        evictCache("roles", "byfunction:" + functionId);
        for (String roleId : roleIds) {
            evictCache("roles", roleId);
            evictCache("roleFunctions", roleId);
        }
        clearCache("userPermissionsVo");
        clearCache("userInfo");
        evictAllLocalCaches();
    }

    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "roles", allEntries = true),
        @CacheEvict(value = "userRoles", allEntries = true),
        @CacheEvict(value = "users", allEntries = true),
        @CacheEvict(value = "userPermissionsVo", allEntries = true),
        @CacheEvict(value = "userInfo", allEntries = true)
    })
    public void roleBindUser(String roleId, List<String> userIds) {
        UUID roleUuid = mapUuid(roleId);
        if (roleUuid == null) {
            throw new IllegalArgumentException("Key must not be null");
        } else if (userIds == null || userIds.isEmpty()) {
            throw new IllegalArgumentException("User list is empty");
        }
        Role role = roleDataAccess.findById(roleUuid).orElseThrow(
                () -> new IllegalArgumentException("Role not found")
        );
        Set<UUID> userUuidSet = userIds.stream()
                .map(UUID::fromString)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<UUID> userUuids = List.copyOf(userUuidSet);
        List<User> users = userDataAccess.findAllById(userUuids);

        if (users.size() != userUuids.size()) {
            throw new IllegalArgumentException("User not found");
        }

        List<UserRole> existingAllRoles = userRoleDataAccess.findByUserIdIn(userUuids);
        Map<UUID, List<UserRole>> existingRolesByUser = existingAllRoles.stream()
                .filter(ur -> ur.getUser() != null)
                .collect(Collectors.groupingBy(ur -> ur.getUser().getId()));

        List<UUID> duplicateUserIds = new ArrayList<>();
        List<UserRole> userRoles = new ArrayList<>();
        for (User user : users) {
            List<UserRole> existingRoles = existingRolesByUser.getOrDefault(user.getId(), List.of());
            long existingBindingCount = existingRoles.stream()
                    .filter(existing -> existing.getRole() != null && roleUuid.equals(existing.getRole().getId()))
                    .count();

            if (existingBindingCount > 1) {
                duplicateUserIds.add(user.getId());
            }

            if (existingBindingCount == 0 || existingBindingCount > 1) {
                UserRole userRole = new UserRole();
                userRole.setRole(role);
                userRole.setUser(user);
                userRoles.add(userRole);
            }
        }

        if (!duplicateUserIds.isEmpty()) {
            userRoleDataAccess.deleteByUserIdsAndRoleId(duplicateUserIds, roleUuid);
        }

        if (userRoles.isEmpty()) {
            return;
        }

        userRoleDataAccess.saveAll(userRoles);

        for (String userId : userIds) {
            evictCache("roles", "byuser:" + userId);
        }
        evictCache("userRoles", "byrole:" + roleId);
        clearCache("userPermissionsVo");
        clearCache("userInfo");
        clearCache("users");
        evictAllLocalCaches();
    }

    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "roles", key = "'byuser:' + #userId"),
        @CacheEvict(value = "userRoles", allEntries = true),
        @CacheEvict(value = "users", allEntries = true),
        @CacheEvict(value = "userPermissionsVo", allEntries = true),
        @CacheEvict(value = "userInfo", allEntries = true)
    })
    public void userBindRole(String userId, List<String> roleIds) {
        UUID userUuid = mapUuid(userId);
        if (userUuid == null) {
            throw new IllegalArgumentException("Key must not be null");
        } else if (roleIds == null) {
            throw new IllegalArgumentException("Role list is required");
        }
        User user = userDataAccess.findById(userUuid).orElseThrow(
                () -> new IllegalArgumentException("User not found")
        );

        Set<UUID> roleUuidSet = roleIds.stream()
                .map(UUID::fromString)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (DEFAULT_ADMIN_EMAIL.equalsIgnoreCase(user.getEmail())) {
            Role adminRole = roleDataAccess.findRoleByName(ADMIN_ROLE_NAME);
            if (adminRole != null && adminRole.getId() != null && !roleUuidSet.contains(adminRole.getId())) {
                throw new AppException("FORBIDDEN_ADMIN_ROLE_UNBIND", "不可移除預設管理員帳號的 admin 角色", 400);
            }
        }

        if (roleIds.isEmpty()) {
            userRoleDataAccess.deleteByUserId(userUuid);
            evictUserLocalCaches(user.getEmail());
            return;
        }

        List<UserRole> existingUserRoles = userRoleDataAccess.findByUserId(userUuid);
        if (existingUserRoles == null) {
            existingUserRoles = List.of();
        }
        Set<UUID> seenRoleIds = new LinkedHashSet<>();
        Set<UUID> duplicateRoleIds = new LinkedHashSet<>();
        for (UserRole existingUserRole : existingUserRoles) {
            if (existingUserRole.getRole() == null || existingUserRole.getRole().getId() == null) {
                continue;
            }
            UUID existingRoleId = existingUserRole.getRole().getId();
            if (!seenRoleIds.add(existingRoleId)) {
                duplicateRoleIds.add(existingRoleId);
            }
        }

        Set<UUID> toRemove = new LinkedHashSet<>(duplicateRoleIds);
        for (UUID currentRoleId : seenRoleIds) {
            if (!roleUuidSet.contains(currentRoleId)) {
                toRemove.add(currentRoleId);
            }
        }
        if (!toRemove.isEmpty()) {
            userRoleDataAccess.deleteByUserIdAndRoleIds(userUuid, toRemove);
        }

        Set<UUID> toAdd = roleUuidSet.stream()
                .filter(targetRoleId -> !seenRoleIds.contains(targetRoleId) || duplicateRoleIds.contains(targetRoleId))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (toAdd.isEmpty()) {
            evictUserLocalCaches(user.getEmail());
            return;
        }

        List<Role> roles = roleDataAccess.findAllById(List.copyOf(toAdd));
        if (roles.size() != toAdd.size()) {
            throw new IllegalArgumentException("Role not found");
        }

        List<UserRole> userRoles = roles.stream().map(role -> {
            UserRole userRole = new UserRole();
            userRole.setRole(role);
            userRole.setUser(user);
            return userRole;
        }).toList();
        userRoleDataAccess.saveAll(userRoles);

        for (String roleId : roleIds) {
            evictCache("userRoles", "byrole:" + roleId);
        }
        evictUserLocalCaches(user.getEmail());
    }

    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "roles", allEntries = true),
        @CacheEvict(value = "userRoles", allEntries = true),
        @CacheEvict(value = "users", allEntries = true),
        @CacheEvict(value = "userPermissionsVo", allEntries = true),
        @CacheEvict(value = "userInfo", allEntries = true)
    })
    public void roleUnbindUser(String roleId, List<String> userIds) {
        UUID roleUuid = mapUuid(roleId);
        if (roleUuid == null) {
            throw new IllegalArgumentException("Key must not be null");
        } else if (userIds == null || userIds.isEmpty()) {
            throw new IllegalArgumentException("User list is empty");
        }
        Role role = roleDataAccess.findById(roleUuid).orElseThrow(
                () -> new IllegalArgumentException("Role not found")
        );
        List<UUID> userUuids = userIds.stream().map(UUID::fromString).toList();
        List<User> users = userDataAccess.findAllById(userUuids);
        if (ADMIN_ROLE_NAME.equalsIgnoreCase(role.getName())
                && users.stream().anyMatch(u -> DEFAULT_ADMIN_EMAIL.equalsIgnoreCase(u.getEmail()))) {
            throw new AppException("FORBIDDEN_ADMIN_ROLE_UNBIND", "不可移除預設管理員帳號的 admin 角色", 400);
        }
        userRoleDataAccess.deleteAllByUserInAndRoleIn(users, List.of(role));

        for (String userId : userIds) {
            evictCache("roles", "byuser:" + userId);
        }
        evictCache("userRoles", "byrole:" + roleId);
        clearCache("users");
        clearCache("userPermissionsVo");
        clearCache("userInfo");
        evictAllLocalCaches();
    }

    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "roles", key = "'byuser:' + #userId"),
        @CacheEvict(value = "userRoles", allEntries = true),
        @CacheEvict(value = "users", allEntries = true),
        @CacheEvict(value = "userPermissionsVo", allEntries = true),
        @CacheEvict(value = "userInfo", allEntries = true)
    })
    public void userUnbindRole(String userId, List<String> roleIds) {
        UUID userUuid = mapUuid(userId);
        if (userUuid == null) {
            throw new IllegalArgumentException("Key must not be null");
        } else if (roleIds == null || roleIds.isEmpty()) {
            throw new IllegalArgumentException("Role list is empty");
        }
        User user = userDataAccess.findById(userUuid).orElseThrow(
                () -> new IllegalArgumentException("User not found")
        );
        List<UUID> roleUuids = roleIds.stream().map(UUID::fromString).toList();
        List<Role> roles = roleDataAccess.findAllById(roleUuids);
        if (DEFAULT_ADMIN_EMAIL.equalsIgnoreCase(user.getEmail())
                && roles.stream().anyMatch(r -> ADMIN_ROLE_NAME.equalsIgnoreCase(r.getName()))) {
            throw new AppException("FORBIDDEN_ADMIN_ROLE_UNBIND", "不可移除預設管理員帳號的 admin 角色", 400);
        }
        userRoleDataAccess.deleteAllByUserInAndRoleIn(List.of(user), roles);

        for (String roleId : roleIds) {
            evictCache("userRoles", "byrole:" + roleId);
        }
        clearCache("users");
        clearCache("userPermissionsVo");
        clearCache("userInfo");
        evictUserLocalCaches(user.getEmail());
    }

    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "roles", key = "'byuser:' + #userId"),
        @CacheEvict(value = "userRoles", allEntries = true),
        @CacheEvict(value = "users", allEntries = true),
        @CacheEvict(value = "userPermissionsVo", allEntries = true),
        @CacheEvict(value = "userInfo", allEntries = true)
    })
    public void userUnbindAllRole(String userId) {
        UUID userUuid = mapUuid(userId);
        if (userUuid == null) {
            throw new IllegalArgumentException("Key must not be null");
        }
        User user = userDataAccess.findById(userUuid).orElseThrow(
                () -> new IllegalArgumentException("User not found")
        );
        if (DEFAULT_ADMIN_EMAIL.equalsIgnoreCase(user.getEmail())) {
            throw new AppException("FORBIDDEN_ADMIN_ROLE_UNBIND", "不可移除預設管理員帳號的 admin 角色", 400);
        }

        List<UserRole> existingUserRoles = userRoleDataAccess.findByUserId(userUuid);
        List<UUID> affectedRoleIds = existingUserRoles == null ? List.of() : existingUserRoles.stream()
                .filter(ur -> ur.getRole() != null && ur.getRole().getId() != null)
                .map(ur -> ur.getRole().getId())
                .distinct()
                .toList();

        userRoleDataAccess.deleteByUserId(userUuid);

        for (UUID roleId : affectedRoleIds) {
            evictCache("userRoles", "byrole:" + roleId);
        }
        clearCache("users");
        clearCache("userPermissionsVo");
        clearCache("userInfo");
        evictUserLocalCaches(user.getEmail());
    }

    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "roles", key = "#roleId"),
        @CacheEvict(value = "roles", key = "'all'"),
        @CacheEvict(value = "roleFunctions", key = "#roleId"),
        @CacheEvict(value = "userPermissionsVo", allEntries = true),
        @CacheEvict(value = "userInfo", allEntries = true)
    })
    public void roleUnbindFunction(String roleId, List<String> functionIds) {
        UUID roleUuid = mapUuid(roleId);
        if (roleUuid == null) {
            throw new IllegalArgumentException("Key must not be null");
        } else if (functionIds == null || functionIds.isEmpty()) {
            throw new IllegalArgumentException("Function list is empty");
        }
        Role role = roleDataAccess.findById(roleUuid).orElseThrow(
                () -> new IllegalArgumentException("Role not found")
        );
        List<UUID> functionUuids = functionIds.stream().map(UUID::fromString).toList();
        List<Function> functions = functionDataAccess.findAllById(functionUuids);
        roleFunctionDataAccess.deleteByFunctionAndRole(functions, List.of(role));
        clearCache("userPermissionsVo");
        clearCache("userInfo");
        evictAllLocalCaches();
    }

    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "roles", allEntries = true),
        @CacheEvict(value = "roleFunctions", allEntries = true),
        @CacheEvict(value = "userPermissionsVo", allEntries = true),
        @CacheEvict(value = "userInfo", allEntries = true)
    })
    public void functionUnbindRole(String functionId, List<String> roleIds) {
        UUID functionUuid = mapUuid(functionId);
        if (functionUuid == null) {
            throw new IllegalArgumentException("Key must not be null");
        } else if (roleIds == null || roleIds.isEmpty()) {
            throw new IllegalArgumentException("Role list is empty");
        }
        Function function = functionDataAccess.findById(functionUuid).orElseThrow(
                () -> new IllegalArgumentException("Function not found")
        );
        List<UUID> roleUuids = roleIds.stream().map(UUID::fromString).toList();
        List<Role> roles = roleDataAccess.findAllById(roleUuids);
        roleFunctionDataAccess.deleteByFunctionAndRole(List.of(function), roles);

        evictCache("roles", "byfunction:" + functionId);
        for (String roleId : roleIds) {
            evictCache("roles", roleId);
            evictCache("roleFunctions", roleId);
        }
        clearCache("userPermissionsVo");
        clearCache("userInfo");
        evictAllLocalCaches();
    }


    @Override
    public List<FunctionVo> getFunctionByRole(String roleId) {
        Supplier<List<FunctionVo>> loader = () -> transactionExecutor.executeReadOnly(() -> {
            UUID roleUuid = mapUuid(roleId);
            if (roleUuid == null) {
                throw new IllegalArgumentException("Key must not be null");
            }
            Role role = roleDataAccess.findByIdWithRoleFunctions(roleUuid).orElseThrow(
                    () -> new IllegalArgumentException("Role not found")
            );
            return role.getRoleFunctions().stream()
                    .map(RoleFunction::getFunction)
                    .map(functionMapper::toVo)
                    .toList();
        });
        if (cacheExecutor == null) {
            return loader.get();
        }
        return cacheExecutor.executeCacheableList("roleFunctions", roleId, loader);
    }

    @Override
    public List<RoleVo> getRoleByFunction(String functionId) {
        Supplier<List<RoleVo>> loader = () -> transactionExecutor.executeReadOnly(() -> {
            UUID functionUuid = mapUuid(functionId);
            if (functionUuid == null) {
                throw new IllegalArgumentException("Key must not be null");
            }
            Function function = functionDataAccess.findByIdWithRoleFunctions(functionUuid).orElseThrow(
                    () -> new IllegalArgumentException("Function not found")
            );
            List<Role> roles = enrichRolesWithFunctions(
                    function.getRoleFunctions().stream()
                            .map(RoleFunction::getRole)
                            .filter(Objects::nonNull)
                            .toList()
            );
            return roles.stream()
                    .map(roleMapper::toVo)
                    .toList();
        });
        if (cacheExecutor == null) {
            return loader.get();
        }
        return cacheExecutor.executeCacheableList("roles", "byfunction:" + functionId, loader);
    }

    @Override
    public List<UserVo> getUserByRole(String roleId) {
        Supplier<List<UserVo>> loader = () -> transactionExecutor.executeReadOnly(() -> {
            UUID roleUuid = mapUuid(roleId);
            if (roleUuid == null) {
                throw new IllegalArgumentException("Key must not be null");
            }
            Role role = roleDataAccess.findByIdWithUserRoles(roleUuid).orElseThrow(
                    () -> new IllegalArgumentException("Role not found")
            );
            List<User> users = enrichUsersWithRoles(
                    role.getUserRoles().stream()
                            .map(UserRole::getUser)
                            .filter(Objects::nonNull)
                            .toList()
            );
            return users.stream()
                    .map(userMapper::toVo)
                    .toList();
        });
        if (cacheExecutor == null) {
            return loader.get();
        }
        return cacheExecutor.executeCacheableList("userRoles", "byrole:" + roleId, loader);
    }

    @Override
    public List<RoleVo> getRoleByUser(String userId) {
        Supplier<List<RoleVo>> loader = () -> transactionExecutor.executeReadOnly(() -> {
            UUID userUuid = mapUuid(userId);
            if (userUuid == null) {
                throw new IllegalArgumentException("Key must not be null");
            }
            User user = userDataAccess.findByIdWithRoles(userUuid).orElseThrow(
                    () -> new IllegalArgumentException("User not found")
            );
            List<Role> roles = enrichRolesWithFunctions(
                    user.getRoles().stream()
                            .map(UserRole::getRole)
                            .filter(Objects::nonNull)
                            .toList()
            );
            return roles.stream()
                    .map(roleMapper::toVo)
                    .toList();
        });
        if (cacheExecutor == null) {
            return loader.get();
        }
        return cacheExecutor.executeCacheableList("roles", "byuser:" + userId, loader);
    }

    @Override
    public RoleVo getRoleByName(String name){
        Supplier<RoleVo> loader = () -> transactionExecutor.executeReadOnly(() -> {
            Role role = roleDataAccess.findRoleByName(name);
            if (role == null) {
                return null;
            }
            List<Role> enriched = enrichRolesWithFunctions(List.of(role));
            return roleMapper.toVo(enriched.getFirst());
        });
        if (cacheExecutor == null) {
            return loader.get();
        }
        return cacheExecutor.executeCacheable("roles", "byname:" + name, loader);
    }
    
    @Override
    public PageResult<RoleVo> searchRoles(RoleSearchQuery query) {
        return transactionExecutor.executeReadOnly(() -> {
            // 驗證排序欄位與方向
            SEARCH_SORT_POLICY.validate(query.getSortBy(), query.getSortDir());
            
            // 執行分頁查詢
            Page<Role> rolePage = roleDataAccess.searchRoles(query);
            
            // 轉換為 VO
            List<RoleVo> roleVos = rolePage.getContent().stream()
                    .map(roleMapper::toSearchVo)
                    .toList();
            
            // 返回分頁結果
            return PageResult.of(rolePage, roleVos);
        });
    }

    @Override
    public Flux<ServerSentEvent<List<RoleVo>>> streamRolesChunked(int chunkSize) {
        int safeChunkSize = Math.max(10, Math.min(chunkSize, 1000));

        Flux<ServerSentEvent<List<RoleVo>>> dataFlux = Flux.<ServerSentEvent<List<RoleVo>>, Integer>generate(
                () -> 0,
                (page, sink) -> {
                    try {
                        Page<RoleVo> rolePage = transactionExecutor.executeReadOnly(() -> {
                            Page<Role> entityPage = roleDataAccess.findAllPaged(
                                    PageRequest.of(page, safeChunkSize, Sort.by(Sort.Direction.ASC, "id"))
                            );

                            if (entityPage.isEmpty()) {
                                return new PageImpl<>(List.<RoleVo>of(), entityPage.getPageable(), entityPage.getTotalElements());
                            }

                            List<RoleVo> voList = entityPage.getContent().stream()
                                    .map(roleMapper::toSearchVo)
                                    .toList();

                            return new PageImpl<>(voList, entityPage.getPageable(), entityPage.getTotalElements());
                        });

                        if (rolePage.isEmpty()) {
                            sink.complete();
                            return page;
                        }

                        sink.next(ServerSentEvent.<List<RoleVo>>builder()
                                .event("chunk")
                                .id(String.valueOf(page))
                                .data(rolePage.getContent())
                                .build());

                        if (!rolePage.hasNext()) {
                            sink.complete();
                            return page;
                        }

                        return page + 1;
                    } catch (Exception ex) {
                        log.error("角色 SSE 分塊串流分頁查詢失敗 [page={}]: {}", page, ex.getMessage(), ex);
                        sink.error(ex);
                        return page;
                    }
                }
        ).subscribeOn(Schedulers.boundedElastic());

        ServerSentEvent<List<RoleVo>> completeEvent = ServerSentEvent.<List<RoleVo>>builder()
                .event("complete")
                .data(List.of())
                .build();

        Flux<ServerSentEvent<List<RoleVo>>> streamWithComplete = Flux.concat(dataFlux, Flux.just(completeEvent));

        Flux<ServerSentEvent<List<RoleVo>>> heartbeatFlux = Flux.interval(Duration.ofSeconds(15))
                .map(tick -> ServerSentEvent.<List<RoleVo>>builder()
                        .comment("keep-alive")
                        .build());

        return Flux.merge(streamWithComplete, heartbeatFlux)
                .takeUntil(event -> "complete".equals(event.event()))
                .doOnCancel(() -> log.info("客戶端中斷角色 SSE 串流連線"))
                .onErrorResume(ex -> {
                    log.error("角色 SSE 串流處理發生異常: {}", ex.getMessage(), ex);
                    return Flux.just(
                            ServerSentEvent.<List<RoleVo>>builder()
                                    .event("error")
                                    .comment("串流處理發生異常，請聯繫管理員")
                                    .build()
                    );
                });
    }

    private UUID mapUuid(String id) {
        return id == null || id.isBlank() ? null : UUID.fromString(id);
    }

    private void evictCache(String cacheName, String key) {
        if (cacheManager == null) {
            return;
        }
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }

    private void clearCache(String cacheName) {
        if (cacheManager == null) {
            return;
        }
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }

    private void syncRoleFunctions(UUID roleId, List<String> functionIds) {
        if (functionIds == null) {
            return;
        }
        Role role = roleDataAccess.findById(roleId).orElseThrow(
                () -> new IllegalArgumentException("Role not found")
        );
        roleFunctionDataAccess.deleteByRoleKey(roleId);
        if (functionIds.isEmpty()) {
            return;
        }
        List<UUID> functionUuids = functionIds.stream()
                .map(UUID::fromString)
                .distinct()
                .toList();
        List<Function> functions = functionDataAccess.findAllById(functionUuids);
        if (functions.size() != functionUuids.size()) {
            throw new IllegalArgumentException("Function not found");
        }
        List<RoleFunction> roleFunctions = functions.stream().map(function -> {
            RoleFunction roleFunction = new RoleFunction();
            roleFunction.setRole(role);
            roleFunction.setFunction(function);
            return roleFunction;
        }).toList();
        roleFunctionDataAccess.saveAll(roleFunctions);
    }

    private boolean isSystemReservedRole(String roleName) {
        return roleName != null && SYSTEM_RESERVED_ROLES.stream()
                .anyMatch(reserved -> reserved.equalsIgnoreCase(roleName.trim()));
    }

    private void evictUserLocalCaches(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        if (iamLocalCacheManager != null) {
            iamLocalCacheManager.evictUser(email);
        }
    }

    private void evictAllLocalCaches() {
        if (iamLocalCacheManager != null) {
            iamLocalCacheManager.evictAll();
        }
    }

    private List<Role> enrichRolesWithFunctions(List<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = roles.stream()
                .map(Role::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return roles;
        }
        List<Role> enriched = roleDataAccess.findAllByIdsWithFunctions(ids);
        if (enriched == null || enriched.isEmpty()) {
            return roles;
        }
        Map<UUID, Role> enrichedMap = enriched.stream()
                .filter(r -> r.getId() != null)
                .collect(Collectors.toMap(Role::getId, r -> r, (first, second) -> first));
        return roles.stream()
                .map(r -> r.getId() != null ? enrichedMap.getOrDefault(r.getId(), r) : r)
                .toList();
    }

    private List<User> enrichUsersWithRoles(List<User> users) {
        if (users == null || users.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = users.stream()
                .map(User::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return users;
        }
        List<User> enriched = userDataAccess.findAllByIdsWithRoles(ids);
        if (enriched == null || enriched.isEmpty()) {
            return users;
        }
        Map<UUID, User> enrichedMap = enriched.stream()
                .filter(u -> u.getId() != null)
                .collect(Collectors.toMap(User::getId, u -> u, (first, second) -> first));
        return users.stream()
                .map(u -> u.getId() != null ? enrichedMap.getOrDefault(u.getId(), u) : u)
                .toList();
    }
}
