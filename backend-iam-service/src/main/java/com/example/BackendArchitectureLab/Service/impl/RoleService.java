package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Dto.Vo.Search.RoleSearchQuery;
import com.example.BackendArchitectureLab.Dto.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Service.IRoleService;
import com.example.BackendArchitectureLab.Util.SortFieldValidator;
import com.example.BackendArchitectureLab.DataAccess.*;
import com.example.BackendArchitectureLab.Mapper.FunctionMapper;
import com.example.BackendArchitectureLab.Mapper.RoleMapper;
import com.example.BackendArchitectureLab.Mapper.UserMapper;
import com.example.BackendArchitectureLab.Dto.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Dto.Vo.RoleOutVo;
import com.example.BackendArchitectureLab.Dto.Vo.UserVo;
import com.example.BackendArchitectureLab.Entity.Function;
import com.example.BackendArchitectureLab.Entity.Role;
import com.example.BackendArchitectureLab.Entity.RoleFunction;
import com.example.BackendArchitectureLab.Entity.User;
import com.example.BackendArchitectureLab.Entity.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
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

@Service
public class RoleService implements IRoleService {
    @Autowired
    private IRoleDataAccess roleDataAccess;
    @Autowired
    private IRoleFunctionDataAccess roleFunctionDataAccess;
    @Autowired
    private IFunctionDataAccess functionDataAccess;
    @Autowired
    private IUserDataAccess userDataAccess;
    @Autowired
    private IUserRoleDataAccess userRoleDataAccess;
    @Autowired
    private RoleMapper roleMapper;
    @Autowired
    private FunctionMapper functionMapper;
    @Autowired
    private UserMapper userMapper;

    @Override
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

    @Override
    @Cacheable(value = "roles", key = "'all'", sync = true)
    public List<RoleOutVo> getRole() {
        return roleDataAccess.findAll().stream().map(roleMapper::toVo).toList();
    }

    @Override
    @Cacheable(value = "roles", key = "#roleId", sync = true)
    public RoleOutVo getRoleById(String roleId) {
        UUID roleUuid = mapUuid(roleId);
        if (roleUuid == null) {
            throw new IllegalArgumentException("Key must not be null");
        }
        Role role = roleDataAccess.findById(roleUuid).orElseThrow(
                () -> new IllegalArgumentException("Role not found")
        );
        return roleMapper.toVo(role);
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
    @Caching(evict = {
        @CacheEvict(value = "roles", allEntries = true),
        @CacheEvict(value = "roleFunctions", allEntries = true)
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
    }

    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "roles", allEntries = true),
        @CacheEvict(value = "userRoles", allEntries = true)
    })
    // 無法精確反推各 user 的 key，保留全量清除
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
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
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
    }

    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "roles", key = "'byuser:' + #userId"),
        @CacheEvict(value = "userRoles", allEntries = true)
    })
    // userRoles 綁定可能增刪多個 role，無法精確反推各 key
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
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

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
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (UUID roleId : toRemove) {
            userRoleDataAccess.deleteByUserIdAndRoleId(userUuid, roleId);
        }

        Set<UUID> toAdd = roleUuidSet.stream()
                .filter(targetRoleId -> !currentRoleIds.contains(targetRoleId))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
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
    }
    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "roles", allEntries = true),
        @CacheEvict(value = "userRoles", allEntries = true)
    })
    // 多個 userIds 無法精確反推，保留全量清除
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

    }
    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "roles", key = "'byuser:' + #userId"),
        @CacheEvict(value = "userRoles", allEntries = true)
    })
    // 多個 roleIds 無法精確反推
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

    }
    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "roles", key = "'byuser:' + #userId"),
        @CacheEvict(value = "userRoles", allEntries = true)
    })
    // userRoles 全部 role 被清除，無法精確反推
    public void userUnbindAllRole(String userId) {
        UUID userUuid = mapUuid(userId);
        if (userUuid == null) {
            throw new IllegalArgumentException("Key must not be null");
        }
        User user = userDataAccess.findById(userUuid).orElseThrow(
                () -> new IllegalArgumentException("User not found")
        );
        List<Role> roles = roleDataAccess.findAll();
        userRoleDataAccess.deleteAllByUserInAndRoleIn(List.of(user), roles);

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
    @Caching(evict = {
        @CacheEvict(value = "roles", allEntries = true),
        @CacheEvict(value = "roleFunctions", allEntries = true)
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

    }


    @Override
    @Cacheable(value = "roleFunctions", key = "#roleId", sync = true)
    public List<FunctionVo> getFunctionByRole(String roleId) {
        UUID roleUuid = mapUuid(roleId);
        if (roleUuid == null) {
            throw new IllegalArgumentException("Key must not be null");
        }
        Role role = roleDataAccess.findById(roleUuid).orElseThrow(
                () -> new IllegalArgumentException("Role not found")
        );
        return role.getRoleFunctions().stream()
                .map(RoleFunction::getFunction)
                .map(functionMapper::toVo)
                .toList();
    }

    @Override
    public List<RoleOutVo> getRoleByFunction(String functionId) {
        UUID functionUuid = mapUuid(functionId);
        if (functionUuid == null) {
            throw new IllegalArgumentException("Key must not be null");
        }
        Function function = functionDataAccess.findById(functionUuid).orElseThrow(
                () -> new IllegalArgumentException("Function not found")
        );
        return function.getRoleFunctions().stream()
                .map(RoleFunction::getRole)
                .map(roleMapper::toVo)
                .toList();
    }

    @Override
    public List<UserVo> getUserByRole(String roleId) {
        UUID roleUuid = mapUuid(roleId);
        if (roleUuid == null) {
            throw new IllegalArgumentException("Key must not be null");
        }
        Role role = roleDataAccess.findById(roleUuid).orElseThrow(
                () -> new IllegalArgumentException("Role not found")
        );
        return role.getUserRoles().stream()
                .map(UserRole::getUser)
                .map(userMapper::toVo)
                .toList();
    }

    @Override
    @Cacheable(value = "roles", key = "'byuser:' + #userId", sync = true)
    public List<RoleOutVo> getRoleByUser(String userId) {
        UUID userUuid = mapUuid(userId);
        if (userUuid == null) {
            throw new IllegalArgumentException("Key must not be null");
        }
        User user = userDataAccess.findById(userUuid).orElseThrow(
                () -> new IllegalArgumentException("User not found")
        );
        return user.getRoles().stream()
                .map(UserRole::getRole)
                .map(roleMapper::toVo)
                .toList();
    }
    @Override
    @Cacheable(value = "roles", key = "'byname:' + #name", sync = true)
    public RoleOutVo getRoleByName(String name){
        Role role = roleDataAccess.findRoleByName(name);
        return role == null ? null : roleMapper.toVo(role);
    }
    
    @Override
    public PageResult<RoleOutVo> searchRoles(RoleSearchQuery query) {
        // 定義允許的排序欄位
        String[] allowedSortFields = {
            "id", "name", "description", "permissions",
            "createdBy", "updatedBy", "createdTime", "updatedTime"
        };
        
        // 驗證排序欄位
        SortFieldValidator.validateSortField(query.getSortBy(), allowedSortFields);
        
        // 驗證排序方向
        SortFieldValidator.validateSortDirection(query.getSortDir());
        
        // 執行分頁查詢
        Page<Role> rolePage = roleDataAccess.searchRoles(query);
        
        // 轉換為 VO
        List<RoleOutVo> roleVos = rolePage.getContent().stream()
                .map(roleMapper::toVo)
                .toList();
        
        // 返回分頁結果
        return PageResult.of(rolePage, roleVos);
    }

    private UUID mapUuid(String id) {
        return id == null || id.isBlank() ? null : UUID.fromString(id);
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
