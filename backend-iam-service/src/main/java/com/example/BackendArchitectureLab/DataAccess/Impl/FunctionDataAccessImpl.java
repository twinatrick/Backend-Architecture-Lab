package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.Dto.Query.FunctionSearchQuery;
import com.example.BackendArchitectureLab.Repository.FunctionRepository;
import com.example.BackendArchitectureLab.DataAccess.IFunctionDataAccess;
import com.example.BackendArchitectureLab.Entity.Function;
import com.example.BackendArchitectureLab.DataAccess.specification.FunctionSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of IFunctionDataAccess.
 * Delegates to Spring Data JPA FunctionRepository.
 */
@Component
@RequiredArgsConstructor
public class FunctionDataAccessImpl implements IFunctionDataAccess {

    private final FunctionRepository functionRepository;

    @Override
    public List<Function> findAllById(List<UUID> ids) {
        return functionRepository.findAllById(ids);
    }

    @Override
    public Optional<Function> findById(UUID id) {
        return functionRepository.findById(id);
    }

    @Override
    public Function save(Function function) {
        return functionRepository.save(function);
    }

    @Override
    public boolean exists(Example<Function> example) {
        return functionRepository.exists(example);
    }

    @Override
    public List<Function> findAll() {
        return functionRepository.findAll();
    }

    @Override
    public List<Function> findAll(Sort sort) {
        return functionRepository.findAll(sort);
    }

    @Override
    public void delete(Function function) {
        functionRepository.delete(function);
    }

    @Override
    public List<Function> saveAll(List<Function> functions) {
        return functionRepository.saveAll(functions);
    }

    @Override
    public void deleteAll(List<Function> functions) {
        functionRepository.deleteAll(functions);
    }

    @Override
    public List<Function> findAllByGrandParentId(List<String> grandParentIds) {
        return functionRepository.findAllByGrandParentId(grandParentIds);
    }

    @Override
    public Function findFunctionByName(String name) {
        return functionRepository.findFunctionByName(name);
    }

    @Override
    public List<Function> findFunctionByNameAndParent(String name, String parent) {
        return functionRepository.findFunctionByNameAndParent(name, parent);
    }

    @Override
    public Optional<Function> findByIdWithRoleFunctions(UUID id) {
        return functionRepository.findByIdWithRoleFunctions(id);
    }
    
    @Override
    public Page<Function> searchFunctions(FunctionSearchQuery query) {
        // 建立排序
        Sort sort = Sort.by(
            "asc".equalsIgnoreCase(query.getNormalizedSortDir()) 
                ? Sort.Direction.ASC 
                : Sort.Direction.DESC,
            query.getSortBy()
        );
        
        // 建立分頁請求
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(), sort);
        
        // 執行查詢
        return functionRepository.findAll(FunctionSpecification.buildSpecification(query), pageable);
    }

    @Override
    public Page<Function> findAllPaged(Pageable pageable) {
        return functionRepository.findAll(pageable);
    }

    @Override
    public List<Function> findAllParentHierarchyByChildIds(List<UUID> childIds) {
        if (childIds == null || childIds.isEmpty()) {
            return List.of();
        }
        return functionRepository.findAllParentHierarchyByChildIds(childIds);
    }

    @Override
    public List<Function> findAllByParentIn(Collection<UUID> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) {
            return List.of();
        }
        List<String> parentIdStrings = parentIds.stream()
                .filter(Objects::nonNull)
                .map(UUID::toString)
                .toList();
        if (parentIdStrings.isEmpty()) {
            return List.of();
        }
        return functionRepository.findAllByParentIn(parentIdStrings);
    }

    @Override
    @Transactional
    public void deleteByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        functionRepository.deleteByIds(ids);
    }

    @Override
    public List<UUID> collectSubtreeIds(Collection<UUID> rootIds) {
        if (rootIds == null || rootIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> visited = new LinkedHashSet<>();
        List<UUID> currentParentIds = new ArrayList<>();
        for (UUID rootId : rootIds) {
            if (rootId != null && visited.add(rootId)) {
                currentParentIds.add(rootId);
            }
        }
        while (!currentParentIds.isEmpty()) {
            List<Function> children = findAllByParentIn(currentParentIds);
            if (children == null || children.isEmpty()) {
                break;
            }
            List<UUID> nextParentIds = new ArrayList<>();
            for (Function child : children) {
                if (child != null && child.getId() != null && visited.add(child.getId())) {
                    nextParentIds.add(child.getId());
                }
            }
            currentParentIds = nextParentIds;
        }
        return new ArrayList<>(visited);
    }
}
