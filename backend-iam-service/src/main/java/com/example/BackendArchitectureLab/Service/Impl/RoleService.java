package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Vo.Cache.CacheListWrapper;
import org.springframework.context.annotation.Lazy;
import com.example.BackendArchitectureLab.Vo.Search.RoleSearchQuery;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Service.IRoleService;
import com.example.BackendArchitectureLab.Util.SearchSortPolicy;
import com.example.BackendArchitectureLab.DataAccess.*;
import com.example.BackendArchitectureLab.Mapper.FunctionMapper;
import com.example.BackendArchitectureLab.Mapper.RoleMapper;
import com.example.BackendArchitectureLab.Mapper.UserMapper;
import com.example.BackendArchitectureLab.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Vo.RoleOutVo;
import com.example.BackendArchitectureLab.Vo.UserVo;
import com.example.BackendArchitectureLab.Entity.Function;
import com.example.BackendArchitectureLab.Entity.Role;
import com.example.BackendArchitectureLab.Entity.RoleFunction;
import com.example.BackendArchitectureLab.Entity.User;
import com.example.BackendArchitectureLab.Entity.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.example.BackendArchitectureLab.Util.TransactionExecutor;

@Service
@RequiredArgsConstructor
public class RoleService implements IRoleService {
    private static final SearchSortPolicy SEARCH_SORT_POLICY = new SearchSortPolicy(
            "id", "name", "description", "permissions",
            "createdBy", "updatedBy", "createdTime", "updatedTime"
    );

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

    @Override
    @Transactional
    @Caching(put = {
        @CachePut(value = "roles", key = "#result.id"),
        @CachePut(value = "roles", key = "'byname:' + #result.name")
    })
    public RoleOutVo addRole(RoleOutVo roleOutVo) {
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
    public RoleOutVo addRoleWithFunctions(RoleOutVo roleOutVo) {
        RoleOutVo savedRole = addRole(roleOutVo);
        syncRoleFunctions(savedRole.getId(), roleOutVo.getFunctionIds());
        return getRoleById(savedRole.getId().toString());
    }

    @Transactional(readOnly = true)
    @Override
    public List<RoleOutVo> getRole() {
        return getRoleListCache().getData();
    }

    @Override
    @Cacheable(value = "roles", key = "'all'", sync = true)
    public CacheListWrapper<RoleOutVo> getRoleListCache() {
        return transactionExecutor.executeReadOnly(() -> {
            List<RoleOutVo> list = roleDataAccess.findAll().stream().map(roleMapper::toVo).toList();
            return new CacheListWrapper<>(list);
        });
    }

    @Override
    @Cacheable(value = "roles", key = "#roleId", sync = true)
    public RoleOutVo getRoleById(String roleId) {
        return transactionExecutor.executeReadOnly(() -> {
            UUID roleUuid = mapUuid(roleId);
            if (roleUuid == null) {
                throw new IllegalArgumentException("Key must not be null");
            }
            Role role = roleDataAccess.findById(roleUuid).orElseThrow(
                    () -> new IllegalArgumentException("Role not found")
            );
            return roleMapper.toVo(role);
        });
    }

    @Override
    @Caching(put = {
        @CachePut(value = "roles", key = "#roleOutVo.id"),
        @CachePut(value = "roles", key = "'byname:' + #roleOutVo.name")
    })
    public RoleOutVo updateRole(RoleOutVo roleOutVo) {
        Role role = roleMapper.toEntity(roleOutVo);
        if (role.getId() == null) {
            throw new IllegalArgumentException("Key must not be null");
        } else if (role.getName() == null) {
            throw new IllegalArgumentException("Name must not be null");
        }
        Role existing = roleDataAccess.findById(role.getId()).orElseThrow(
                () -> new IllegalArgumentException("Role not found")
        );
        existing.setName(role.getName());
        existing.setDescription(role.getDescription());
        return roleMapper.toVo(roleDataAccess.save(existing));
    }

    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "roleFunctions", key = "#roleOutVo.id")
    })
    public RoleOutVo updateRoleWithFunctions(RoleOutVo roleOutVo) {
        RoleOutVo updatedRole = updateRole(roleOutVo);
        syncRoleFunctions(updatedRole.getId(), roleOutVo.getFunctionIds());
        return getRoleById(updatedRole.getId().toString());
    }

    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "roles", key = "#roleOutVo.id"),
        @CacheEvict(value = "roleFunctions", key = "#roleOutVo.id")
    })
    public void deleteRole(RoleOutVo roleOutVo) {
        Role role = roleMapper.toEntity(roleOutVo);
        if (role.getId() == null) {
            throw new IllegalArgumentException("Key must not be null");
        }
        role = roleDataAccess.findById(role.getId()).orElseThrow(
                () -> new IllegalArgumentException("Role not found")
        );
        RoleFunction roleFunction = new RoleFunction();
        roleFunction.setRole(role);
        Example<RoleFunction> example = Example.of(roleFunction);
        List<RoleFunction> roleFunctions = roleFunctionDataAccess.findAll(example);
        roleFunctionDataAccess.deleteAll(roleFunctions);
        roleDataAccess.delete(role);

    }
    @Transactional
    @Override
    @CacheEvict(value = "roleFunctions", key = "#roleId")
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
    }

    @Transactional
    @Override
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
            evictCache("roleFunctions", roleId);
        }
    }

    @Transactional
    @Override
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

        List<UserRole> userRoles = new ArrayList<>();
        for (User user : users) {
            List<UserRole> existingRoles = userRoleDataAccess.findByUserId(user.getId());
            long existingBindingCount = existingRoles.stream()
                    .filter(existing -> existing.getRole() != null && roleUuid.equals(existing.getRole().getId()))
                    .count();

            if (existingBindingCount > 1) {
                userRoleDataAccess.deleteByUserIdAndRoleId(user.getId(), roleUuid);
            }

            if (existingBindingCount == 0 || existingBindingCount > 1) {
                UserRole userRole = new UserRole();
                userRole.setRole(role);
                userRole.setUser(user);
                userRoles.add(userRole);
            }
        }

        if (userRoles.isEmpty()) {
            return;
        }

        userRoleDataAccess.saveAll(userRoles);

        for (String userId : userIds) {
            evictCache("roles", "byuser:" + userId);
        }
        evictCache("userRoles", "byrole:" + roleId);
    }

    @Transactional
    @Override
    @CacheEvict(value = "roles", key = "'byuser:' + #userId")
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

        if (roleIds.isEmpty()) {
            userRoleDataAccess.deleteByUserId(userUuid);
            return;
        }

        Set<UUID> roleUuidSet = roleIds.stream()
                .map(UUID::fromString)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<UserRole> existingUserRoles = userRoleDataAccess.findByUserId(userUuid);
        if (existingUserRoles == null) {
            existingUserRoles = List.of();
        }
        Map<UUID, UserRole> existingMap = new HashMap<>();
        for (UserRole existingUserRole : existingUserRoles) {
            UUID existingRoleId = existingUserRole.getRole().getId();
            if (existingMap.containsKey(existingRoleId)) {
                userRoleDataAccess.deleteByUserIdAndRoleId(userUuid, existingRoleId);
                continue;
            }
            existingMap.put(existingRoleId, existingUserRole);
        }

        Set<UUID> currentRoleIds = new LinkedHashSet<>(existingMap.keySet());
        Set<UUID> toRemove = currentRoleIds.stream()
                .filter(currentRoleId -> !roleUuidSet.contains(currentRoleId))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (UUID roleId : toRemove) {
            userRoleDataAccess.deleteByUserIdAndRoleId(userUuid, roleId);
        }

        Set<UUID> toAdd = roleUuidSet.stream()
                .filter(targetRoleId -> !currentRoleIds.contains(targetRoleId))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (toAdd.isEmpty()) {
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
    }
    @Transactional
    @Override
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
        userRoleDataAccess.deleteAllByUserInAndRoleIn(users, List.of(role));

        for (String userId : userIds) {
            evictCache("roles", "byuser:" + userId);
        }
        evictCache("userRoles", "byrole:" + roleId);
    }
    @Transactional
    @Override
    @CacheEvict(value = "roles", key = "'byuser:' + #userId")
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
        userRoleDataAccess.deleteAllByUserInAndRoleIn(List.of(user), roles);

        for (String roleId : roleIds) {
            evictCache("userRoles", "byrole:" + roleId);
        }
    }
    @Transactional
    @Override
    @CacheEvict(value = "roles", key = "'byuser:' + #userId")
    public void userUnbindAllRole(String userId) {
        UUID userUuid = mapUuid(userId);
        if (userUuid == null) {
            throw new IllegalArgumentException("Key must not be null");
        }
        User user = userDataAccess.findById(userUuid).orElseThrow(
                () -> new IllegalArgumentException("User not found")
        );

        List<UserRole> existingUserRoles = userRoleDataAccess.findByUserId(userUuid);
        List<UUID> affectedRoleIds = existingUserRoles.stream()
                .map(ur -> ur.getRole().getId())
                .distinct()
                .toList();

        List<Role> roles = roleDataAccess.findAll();
        userRoleDataAccess.deleteAllByUserInAndRoleIn(List.of(user), roles);

        for (UUID roleId : affectedRoleIds) {
            evictCache("userRoles", "byrole:" + roleId);
        }
    }
    @Transactional
    @Override
    @CacheEvict(value = "roleFunctions", key = "#roleId")
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

    }

    @Transactional
    @Override
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
            evictCache("roleFunctions", roleId);
        }
    }


    @Override
    @Transactional(readOnly = true)
    public List<FunctionVo> getFunctionByRole(String roleId) {
        return getFunctionByRoleCache(roleId).getData();
    }

    @Override
    @Cacheable(value = "roleFunctions", key = "#roleId", sync = true)
    public CacheListWrapper<FunctionVo> getFunctionByRoleCache(String roleId) {
        return transactionExecutor.executeReadOnly(() -> {
            UUID roleUuid = mapUuid(roleId);
            if (roleUuid == null) {
                throw new IllegalArgumentException("Key must not be null");
            }
            Role role = roleDataAccess.findByIdWithRoleFunctions(roleUuid).orElseThrow(
                    () -> new IllegalArgumentException("Role not found")
            );
            List<FunctionVo> list = role.getRoleFunctions().stream()
                    .map(RoleFunction::getFunction)
                    .map(functionMapper::toVo)
                    .toList();
            return new CacheListWrapper<>(list);
        });
    }

    @Transactional(readOnly = true)
    @Override
    public List<RoleOutVo> getRoleByFunction(String functionId) {
        return getRoleByFunctionCache(functionId).getData();
    }

    @Override
    @Cacheable(value = "roles", key = "'byfunction:' + #functionId", sync = true)
    public CacheListWrapper<RoleOutVo> getRoleByFunctionCache(String functionId) {
        return transactionExecutor.executeReadOnly(() -> {
            UUID functionUuid = mapUuid(functionId);
            if (functionUuid == null) {
                throw new IllegalArgumentException("Key must not be null");
            }
            Function function = functionDataAccess.findByIdWithRoleFunctions(functionUuid).orElseThrow(
                    () -> new IllegalArgumentException("Function not found")
            );
            List<RoleOutVo> list = function.getRoleFunctions().stream()
                    .map(RoleFunction::getRole)
                    .map(roleMapper::toVo)
                    .toList();
            return new CacheListWrapper<>(list);
        });
    }

    @Transactional(readOnly = true)
    @Override
    public List<UserVo> getUserByRole(String roleId) {
        return getUserByRoleCache(roleId).getData();
    }

    @Override
    @Cacheable(value = "userRoles", key = "'byrole:' + #roleId", sync = true)
    public CacheListWrapper<UserVo> getUserByRoleCache(String roleId) {
        return transactionExecutor.executeReadOnly(() -> {
            UUID roleUuid = mapUuid(roleId);
            if (roleUuid == null) {
                throw new IllegalArgumentException("Key must not be null");
            }
            Role role = roleDataAccess.findByIdWithUserRoles(roleUuid).orElseThrow(
                    () -> new IllegalArgumentException("Role not found")
            );
            List<UserVo> list = role.getUserRoles().stream()
                    .map(UserRole::getUser)
                    .map(userMapper::toVo)
                    .toList();
            return new CacheListWrapper<>(list);
        });
    }

    @Transactional(readOnly = true)
    @Override
    public List<RoleOutVo> getRoleByUser(String userId) {
        return getRoleByUserListCache(userId).getData();
    }

    @Override
    @Cacheable(value = "roles", key = "'byuser:' + #userId", sync = true)
    public CacheListWrapper<RoleOutVo> getRoleByUserListCache(String userId) {
        return transactionExecutor.executeReadOnly(() -> {
            UUID userUuid = mapUuid(userId);
            if (userUuid == null) {
                throw new IllegalArgumentException("Key must not be null");
            }
            User user = userDataAccess.findByIdWithRoles(userUuid).orElseThrow(
                    () -> new IllegalArgumentException("User not found")
            );
            List<RoleOutVo> list = user.getRoles().stream()
                    .map(UserRole::getRole)
                    .map(roleMapper::toVo)
                    .toList();
            return new CacheListWrapper<>(list);
        });
    }

    @Override
    @Cacheable(value = "roles", key = "'byname:' + #name", sync = true)
    public RoleOutVo getRoleByName(String name){
        return transactionExecutor.executeReadOnly(() -> {
            Role role = roleDataAccess.findRoleByName(name);
            return role == null ? null : roleMapper.toVo(role);
        });
    }
    
    @Override
    @Cacheable(value = "roles", key = "'search:' + #query.toString()", sync = true)
    public PageResult<RoleOutVo> searchRoles(RoleSearchQuery query) {
        return transactionExecutor.executeReadOnly(() -> {
            // 驗證排序欄位與方向
            SEARCH_SORT_POLICY.validate(query.getSortBy(), query.getSortDir());
            
            // 執行分頁查詢
            Page<Role> rolePage = roleDataAccess.searchRoles(query);
            
            // 轉換為 VO
            List<RoleOutVo> roleVos = rolePage.getContent().stream()
                    .map(roleMapper::toVo)
                    .toList();
            
            // 返回分頁結果
            return PageResult.of(rolePage, roleVos);
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
}
