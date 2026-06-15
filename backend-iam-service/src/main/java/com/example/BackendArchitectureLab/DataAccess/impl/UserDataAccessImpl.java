package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.Dto.Vo.Search.UserSearchQuery;
import com.example.BackendArchitectureLab.Repository.UserRepository;
import com.example.BackendArchitectureLab.DataAccess.IUserDataAccess;
import com.example.BackendArchitectureLab.Entity.User;
import com.example.BackendArchitectureLab.DataAccess.specification.UserSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of IUserDataAccess.
 * Delegates to Spring Data JPA UserRepository.
 */
@Component
public class UserDataAccessImpl implements IUserDataAccess {

    @Autowired
    private UserRepository userRepository;

    @Override
    public void save(User user) {
        userRepository.save(user);
    }

    @Override
    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    public Optional<User> findById(UUID key) {
        return userRepository.findById(key);
    }

    @Override
    public boolean existsById(UUID key) {
        return userRepository.existsById(key);
    }

    @Override
    public List<User> findAllById(List<UUID> keys) {
        return userRepository.findAllById(keys);
    }
    
    @Override
    public Page<User> searchUsers(UserSearchQuery query) {
        Sort sort = Sort.by(
            "asc".equalsIgnoreCase(query.getNormalizedSortDir()) 
                ? Sort.Direction.ASC 
                : Sort.Direction.DESC,
            query.getSortBy()
        );
        
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(), sort);
        
        return userRepository.findAll(UserSpecification.buildSpecification(query), pageable);
    }
}
