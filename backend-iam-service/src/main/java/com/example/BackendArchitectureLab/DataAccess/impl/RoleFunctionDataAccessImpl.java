package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.Repository.RoleFunctionRepository;
import com.example.BackendArchitectureLab.DataAccess.IRoleFunctionDataAccess;
import com.example.BackendArchitectureLab.Entity.Function;
import com.example.BackendArchitectureLab.Entity.Role;
import com.example.BackendArchitectureLab.Entity.RoleFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of IRoleFunctionDataAccess.
 * Delegates to Spring Data JPA RoleFunctionRepository.
 */
@Component
public class RoleFunctionDataAccessImpl implements IRoleFunctionDataAccess {

    @Autowired
    private RoleFunctionRepository roleFunctionRepository;

    @Override
    public List<RoleFunction> findAll(Example<RoleFunction> example) {
        return roleFunctionRepository.findAll(example);
    }

    @Override
    public void deleteAll(List<RoleFunction> roleFunctions) {
        roleFunctionRepository.deleteAll(roleFunctions);
    }

    @Override
    public void deleteByFunctionAndRole(List<Function> functions, List<Role> roles) {
        roleFunctionRepository.deleteByFunctionAndRole(functions, roles);
    }

    @Override
    public List<RoleFunction> saveAll(List<RoleFunction> roleFunctions) {
        return roleFunctionRepository.saveAll(roleFunctions);
    }

    @Override
    public void deleteByFunction(UUID functionId) {
        roleFunctionRepository.deleteByFunction(functionId);
    }

    @Override
    public void deleteByRoleKey(UUID roleId) {
        roleFunctionRepository.deleteByRoleKey(roleId);
    }

    @Override
    public void deleteAllByFunctionIn(Collection<Function> functions) {
        roleFunctionRepository.deleteAllByFunctionIn(functions);
    }
}
