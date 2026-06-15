package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.Dto.Vo.Search.RoleSearchQuery;
import com.example.BackendArchitectureLab.Repository.RoleRepository;
import com.example.BackendArchitectureLab.DataAccess.IRoleDataAccess;
import com.example.BackendArchitectureLab.Entity.Role;
import com.example.BackendArchitectureLab.DataAccess.specification.RoleSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of IRoleDataAccess.
 * Delegates to Spring Data JPA RoleRepository.
 */
@Component
public class RoleDataAccessImpl implements IRoleDataAccess {

    @Autowired
    private RoleRepository roleRepository;

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
        
        // 執行查詢
        return roleRepository.findAll(RoleSpecification.buildSpecification(query), pageable);
    }
}
