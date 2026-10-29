package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IFunctionDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IRoleFunctionDataAccess;
import com.example.BackendArchitectureLab.Entity.Function;
import com.example.BackendArchitectureLab.Mapper.FunctionMapper;
import com.example.BackendArchitectureLab.Service.IFunctionCommandService;
import com.example.BackendArchitectureLab.Util.IamLocalCacheManager;
import com.example.BackendArchitectureLab.Vo.FunctionVo;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FunctionCommandService implements IFunctionCommandService {
    private final IFunctionDataAccess functionDataAccess;
    private final IRoleFunctionDataAccess roleFunctionDataAccess;
    private final FunctionMapper functionMapper;
    private final IamLocalCacheManager iamLocalCacheManager;

    @Override
    @Transactional
    @Caching(put = {
        @CachePut(value = "functions", key = "#result.id"),
        @CachePut(value = "functions", key = "'byname:' + #result.name")
    }, evict = {
        @CacheEvict(value = "functions", key = "'bynameparent:' + #result.name + ':' + (#result.parent != null ? #result.parent : '')"),
        @CacheEvict(value = "functions", key = "'all'")
    })
    public FunctionVo addFunction(FunctionVo functionVo) {
        Function function = functionMapper.toEntity(functionVo);
        if (function.getId() != null) {
            throw new IllegalArgumentException("Key must be null");
        } else if (function.getName() == null) {
            throw new IllegalArgumentException("Name must not be null");
        } else {
            String normalizedParent = function.getParent() != null ? function.getParent() : "";
            function.setParent(normalizedParent);
            if (!functionDataAccess.findFunctionByNameAndParent(function.getName(), normalizedParent).isEmpty()) {
                throw new IllegalArgumentException("Name already exists");
            }
        }

        FunctionVo saved = functionMapper.toVo(functionDataAccess.save(function));
        evictLocalPermissionCache();
        return saved;
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "functions", key = "#functionVo.id"),
        @CacheEvict(value = "functions", key = "'byname:' + #functionVo.name"),
        @CacheEvict(value = "functions", key = "'bynameparent:' + #functionVo.name + ':' + (#functionVo.parent != null ? #functionVo.parent : '')"),
        @CacheEvict(value = "functions", key = "'all'"),
        @CacheEvict(value = "roles", allEntries = true),
        @CacheEvict(value = "roleFunctions", allEntries = true),
        @CacheEvict(value = "userPermissionsVo", allEntries = true),
        @CacheEvict(value = "userInfo", allEntries = true)
    })
    public void updateFunction(FunctionVo functionVo) {
        Function function = functionMapper.toEntity(functionVo);
        if (function.getId() == null) {
            throw new IllegalArgumentException("Key must not be null");
        } else if (function.getName() == null) {
            throw new IllegalArgumentException("Name must not be null");
        }
        if (functionDataAccess.findById(function.getId()).isEmpty()) {
            throw new IllegalArgumentException("Function not found");
        }
        String normalizedParent = function.getParent() != null ? function.getParent() : "";
        function.setParent(normalizedParent);
        boolean duplicateSibling = functionDataAccess.findFunctionByNameAndParent(function.getName(), normalizedParent)
                .stream()
                .anyMatch(existing -> existing.getId() != null && !existing.getId().equals(function.getId()));
        if (duplicateSibling) {
            throw new IllegalArgumentException("Name already exists");
        }
        functionDataAccess.save(function);
        evictLocalPermissionCache();
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "functions", allEntries = true),
        @CacheEvict(value = "roles", allEntries = true),
        @CacheEvict(value = "roleFunctions", allEntries = true),
        @CacheEvict(value = "userPermissionsVo", allEntries = true),
        @CacheEvict(value = "userInfo", allEntries = true)
    })
    public void deleteFunction(FunctionVo functionVo) {
        Function function = functionMapper.toEntity(functionVo);
        if (function.getId() == null) {
            throw new IllegalArgumentException("Key must not be null");
        }
        List<UUID> subtreeIds = functionDataAccess.collectSubtreeIds(List.of(function.getId()));
        roleFunctionDataAccess.deleteByFunctionIds(subtreeIds);
        functionDataAccess.deleteByIds(subtreeIds);
        evictLocalPermissionCache();
    }

    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "functions", allEntries = true),
        @CacheEvict(value = "roles", allEntries = true),
        @CacheEvict(value = "roleFunctions", allEntries = true),
        @CacheEvict(value = "userPermissionsVo", allEntries = true),
        @CacheEvict(value = "userInfo", allEntries = true)
    })
    public void deleteFunction(List<FunctionVo> function) {
        // 批次刪除向下遞迴收集子孫節點並一併清除綁定與節點
        if (function == null || function.isEmpty()) {
            return;
        }
        List<UUID> rootIds = function.stream()
                .map(FunctionVo::getId)
                .filter(id -> id != null && !id.isBlank())
                .map(UUID::fromString)
                .toList();
        if (rootIds.isEmpty()) {
            return;
        }
        List<UUID> subtreeIds = functionDataAccess.collectSubtreeIds(rootIds);
        roleFunctionDataAccess.deleteByFunctionIds(subtreeIds);
        functionDataAccess.deleteByIds(subtreeIds);
        evictLocalPermissionCache();
    }

    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "functions", allEntries = true),
        @CacheEvict(value = "roles", allEntries = true),
        @CacheEvict(value = "roleFunctions", allEntries = true),
        @CacheEvict(value = "userPermissionsVo", allEntries = true),
        @CacheEvict(value = "userInfo", allEntries = true)
    })
    public void saveFunction(List<FunctionVo> function) {
        // 批次儲存無法精確反推 key，保留全量清除
        if (function == null || function.isEmpty()) {
            return;
        }
        List<Function> f = function.stream()
                .map(functionMapper::toEntity)
                .toList();

        functionDataAccess.saveAll(f);
        evictLocalPermissionCache();
    }

    private void evictLocalPermissionCache() {
        if (iamLocalCacheManager != null) {
            iamLocalCacheManager.evictAllPermissions();
        }
    }
}
