package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.Dto.Query.RoleSearchQuery;
import com.example.BackendArchitectureLab.Repository.RoleRepository;
import com.example.BackendArchitectureLab.DataAccess.IRoleDataAccess;
import com.example.BackendArchitectureLab.Entity.Role;
import com.example.BackendArchitectureLab.DataAccess.specification.RoleSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of IRoleDataAccess.
 * Delegates to Spring Data JPA RoleRepository.
 */
@Component
@RequiredArgsConstructor
public class RoleDataAccessImpl implements IRoleDataAccess {

    private final RoleRepository roleRepository;

    @Override
    public List<Role> findRoleByIdIn(List<UUID> ids) {
        return roleRepository.findRoleByIdIn(ids);
    }

    @Override
    public boolean exists(Example<Role> example) {
        return roleRepository.exists(example);
    }

    @Override
    public Role save(Role role) {
        return roleRepository.save(role);
    }

    @Override
    public List<Role> findAll() {
        return roleRepository.findAll();
    }

    @Override
    public Optional<Role> findById(UUID key) {
        return roleRepository.findById(key);
    }

    @Override
    public void delete(Role role) {
        roleRepository.delete(role);
    }

    @Override
    public List<Role> findAllById(List<UUID> keys) {
        return roleRepository.findAllById(keys);
    }

    @Override
    public Role findRoleByName(String name) {
        return roleRepository.findRoleByName(name);
    }

    @Override
    public Optional<Role> findByIdWithRoleFunctions(UUID key) {
        return roleRepository.findByIdWithRoleFunctions(key);
    }

    @Override
    public Optional<Role> findByIdWithUserRoles(UUID key) {
        return roleRepository.findByIdWithUserRoles(key);
    }
    
    @Override
    public List<Role> findAllByIdsWithFunctions(List<UUID> ids) {
        return roleRepository.findAllByIdsWithFunctions(ids);
    }

    @Override
    public Page<Role> searchRoles(RoleSearchQuery query) {
        // 建立排序
        Sort sort = Sort.by(
            "asc".equalsIgnoreCase(query.getNormalizedSortDir()) 
                ? Sort.Direction.ASC 
                : Sort.Direction.DESC,
            query.getSortBy()
        );
        
        // 建立分頁請求
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(), sort);
        
        // 第一次查詢：取得分頁 ID 結果
        Page<Role> page = roleRepository.findAll(RoleSpecification.buildSpecification(query), pageable);
        
        if (page.isEmpty()) {
            return page;
        }
        
        // 第二次查詢：僅以 GROUP BY 聚合統計各角色的 functionCount，避免三表 LEFT JOIN FETCH 拉取全量關聯實體
        List<UUID> ids = page.getContent().stream().map(Role::getId).toList();
        List<Object[]> countRows = roleRepository.countFunctionsByRoleIds(ids);
        Map<UUID, Integer> countMap = countRows.stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> ((Number) row[1]).intValue(),
                        (existing, replacement) -> existing
                ));

        page.getContent().forEach(role -> role.setFunctionCount(countMap.getOrDefault(role.getId(), 0)));

        return new PageImpl<>(page.getContent(), pageable, page.getTotalElements());
    }

    @Override
    public Page<Role> findAllPaged(Pageable pageable) {
        return roleRepository.findAll(pageable);
    }
}
