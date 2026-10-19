package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.Vo.Search.RoleSearchQuery;
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
        
        // 第二次查詢：批量載入含 roleFunctions + function 的完整資料，解決 N+1 問題
        List<UUID> ids = page.getContent().stream().map(Role::getId).toList();
        Map<UUID, Role> roleMap = roleRepository.findAllByIdsWithFunctions(ids)
                .stream().collect(Collectors.toMap(Role::getId, r -> r));
        
        List<Role> enrichedRoles = page.getContent().stream()
                .map(r -> roleMap.getOrDefault(r.getId(), r))
                .toList();
        
        return new PageImpl<>(enrichedRoles, pageable, page.getTotalElements());
    }

    @Override
    public Page<Role> findAllPaged(Pageable pageable) {
        return roleRepository.findAll(pageable);
    }
}
