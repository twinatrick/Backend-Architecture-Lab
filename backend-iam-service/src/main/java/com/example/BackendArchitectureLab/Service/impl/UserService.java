package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Dto.Vo.Search.UserSearchQuery;
import com.example.BackendArchitectureLab.Dto.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Service.IRoleService;
import com.example.BackendArchitectureLab.Service.IUserService;
import com.example.BackendArchitectureLab.Util.SortFieldValidator;
import com.example.BackendArchitectureLab.DataAccess.IFunctionDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserDataAccess;
import com.example.BackendArchitectureLab.Feign.ProjectServiceFeignClient;
import com.example.BackendArchitectureLab.Mapper.FunctionMapper;
import com.example.BackendArchitectureLab.Mapper.UserMapper;
import com.example.BackendArchitectureLab.Entity.Function;
import com.example.BackendArchitectureLab.Entity.User;
import com.example.BackendArchitectureLab.Dto.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Dto.Vo.UserVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class UserService implements IUserService {

    @Autowired
    private IUserDataAccess userDataAccess;
    @Autowired
    private IRoleService roleService;
    @Autowired
    private IFunctionDataAccess functionDataAccess;
    @Autowired
    private ProjectServiceFeignClient projectServiceFeignClient;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private FunctionMapper functionMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Caching(put = {
        @CachePut(value = "users", key = "#result.id"),
        @CachePut(value = "users", key = "#result.email")
    })
    @Override
    public UserVo createUser(UserVo userVo) {
        User user = userMapper.toEntity(userVo);
        if (user.getPassword() != null && !user.getPassword().startsWith("{") && !user.getPassword().startsWith("$2a$") && !user.getPassword().startsWith("$2b$") && !user.getPassword().startsWith("$2y$")) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        userDataAccess.save(user);
        return userMapper.toVo(user);
    }

    @Override
    public List<UserVo> getUser() {
        return userDataAccess.findAll().stream().map(userMapper::toVo).toList();
    }
    @Override
    public List<UserVo> getUserByEmail(String email) {
        return userDataAccess.findByEmail(email).map(userMapper::toVo).map(List::of).orElseGet(List::of);
    }
    @Cacheable(value = "users", key = "#email", sync = true)
    @Override
    public UserVo getOnlyUserByEmail(String email) {
        User user = userDataAccess.findByEmail(email).orElseThrow(
                () -> new IllegalArgumentException("User not found")
        );
        return userMapper.toVo(user);
    }
    @Cacheable(value = "users", key = "#id", sync = true)
    @Override
    public UserVo getUserById(String id) {
        UUID userId = mapUuid(id);
        if (userId == null) {
            throw new IllegalArgumentException("Key must not be null");
        }
        User user = userDataAccess.findById(userId).orElseThrow(
                () -> new IllegalArgumentException("User not found")
        );
        return userMapper.toVo(user);
    }

    @Caching(put = {
        @CachePut(value = "users", key = "#result.id"),
        @CachePut(value = "users", key = "#result.email")
    })
    @Override
    public UserVo saveUser(UserVo userVo) {
        User user = userMapper.toEntity(userVo);
        if (user.getPassword() != null && !user.getPassword().startsWith("{") && !user.getPassword().startsWith("$2a$") && !user.getPassword().startsWith("$2b$") && !user.getPassword().startsWith("$2y$")) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        userDataAccess.save(user);
        return userMapper.toVo(user);
    }

    @Override
    @Transactional
    @CacheEvict(value = "users", allEntries = true)
    public void saveUserWithRole(UserVo userVo) {
        if (userVo.getRoleArr() == null) {
            throw new IllegalArgumentException("Role list is required");
        }
        if(userVo.getId() == null|| userVo.getId().isEmpty()){
            User user = new User();
            user.setEmail(userVo.getEmail());
            user.setPassword(passwordEncoder.encode(userVo.getPassword()));
            user.setDisabled(userVo.isDisabled());
            userDataAccess.save(user);
            roleService.userBindRole(user.getId().toString(), userVo.getRoleArr());
            return;
        }
        User u = userDataAccess.findByEmail(userVo.getEmail()).orElseThrow(
                () -> new IllegalArgumentException("User not found")
        );
        u.setDisabled(userVo.isDisabled());
        if (userVo.getPassword() != null && !userVo.getPassword().startsWith("{") && !userVo.getPassword().startsWith("$2a$") && !userVo.getPassword().startsWith("$2b$") && !userVo.getPassword().startsWith("$2y$")) {
            u.setPassword(passwordEncoder.encode(userVo.getPassword()));
        }
        userDataAccess.save(u);
        roleService.userBindRole(u.getId().toString(), userVo.getRoleArr());

    }

    @Override
    public List<FunctionVo> getAllParent(List<String> child){
        List<UUID> childUUID = child.stream().map(UUID::fromString).toList();
        List<Function> functions = functionDataAccess.findAllById(childUUID);
        List<UUID> parentUUID = functions.stream().map(Function::getParent).filter(parent -> !parent.isEmpty()).map(UUID::fromString).toList();
        List<Function> parentFunctions = functionDataAccess.findAllById(parentUUID);


        List<String> result = new ArrayList<>(parentFunctions.stream().map(Function::getId).map(UUID::toString).toList());
        parentFunctions.stream().map(Function::getParent).forEach(result::add);
        List<Function> parentParentFunctions = functionDataAccess.findAllById(result.stream().filter((x)->!x.isEmpty()).map(UUID::fromString).toList());


        return parentParentFunctions.stream().map(functionMapper::toVo).toList();
    }

    private String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalArgumentException("User not authenticated");
        }
        return auth.getName();
    }

    @Override
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
    public List<UserVo> getAllUsersVo() {
        return getUser();
    }

    @Override
    @Transactional
    public void bindUserProject(String userId, String projectId) {
        UUID userUuid = mapUuid(userId);
        UUID projectUuid = mapUuid(projectId);
        if (userUuid == null || projectUuid == null) {
            throw new IllegalArgumentException("Key must not be null");
        }

        if (projectServiceFeignClient.existsUserProject(userUuid, projectUuid)) {
            throw new IllegalArgumentException("Project already bind to user");
        }

        projectServiceFeignClient.saveUserProject(userUuid, projectUuid);
    }

    @Override
    @Transactional
    @CacheEvict(value = "userProjects", key = "'current:' + #userId")
    public void rebindUserProjects(UUID userId, List<UUID> projectIds) {
        if (userId == null) {
            throw new IllegalArgumentException("Key must not be null");
        }

        userDataAccess.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Set<UUID> targetProjectIds = projectIds == null
                ? Set.of()
                : projectIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<UUID> existingIds = projectServiceFeignClient.getUserProjectIds(userId);
        Set<UUID> existingProjectIds = new HashSet<>(
                existingIds != null ? existingIds : List.of());

        for (UUID existingProjectId : existingProjectIds) {
            if (!targetProjectIds.contains(existingProjectId)) {
                projectServiceFeignClient.deleteUserProject(userId, existingProjectId);
            }
        }

        for (UUID targetProjectId : targetProjectIds) {
            if (!existingProjectIds.contains(targetProjectId)) {
                projectServiceFeignClient.saveUserProject(userId, targetProjectId);
            }
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = "users", key = "#userId")
    public void rebindUserRoles(UUID userId, List<String> roleIds) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }
        if (roleIds == null) {
            throw new IllegalArgumentException("Role list is required");
        }

        userDataAccess.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        roleService.userBindRole(userId.toString(), roleIds);
    }

    private UUID mapUuid(String id) {
        return id == null || id.isBlank() ? null : UUID.fromString(id);
    }
    
    @Override
    public PageResult<UserVo> searchUsers(UserSearchQuery query) {
        String[] allowedSortFields = {
            "id", "name", "email", "phone", "disabled", 
            "createdBy", "updatedBy", "createdTime", "updatedTime"
        };
        
        SortFieldValidator.validateSortField(query.getSortBy(), allowedSortFields);
        
        SortFieldValidator.validateSortDirection(query.getSortDir());
        
        Page<User> userPage = userDataAccess.searchUsers(query);
        
        List<UserVo> userVos = userPage.getContent().stream()
                .map(userMapper::toVo)
                .toList();
        
        return PageResult.of(userPage, userVos);
    }
}
