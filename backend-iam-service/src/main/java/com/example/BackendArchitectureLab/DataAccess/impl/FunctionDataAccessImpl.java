package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.Dto.Vo.Search.FunctionSearchQuery;
import com.example.BackendArchitectureLab.Repository.FunctionRepository;
import com.example.BackendArchitectureLab.DataAccess.IFunctionDataAccess;
import com.example.BackendArchitectureLab.Entity.Function;
import com.example.BackendArchitectureLab.DataAccess.specification.FunctionSpecification;
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
 * Implementation of IFunctionDataAccess.
 * Delegates to Spring Data JPA FunctionRepository.
 */
@Component
public class FunctionDataAccessImpl implements IFunctionDataAccess {

    @Autowired
    private FunctionRepository functionRepository;

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
}
