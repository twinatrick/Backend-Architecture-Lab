package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.Dto.Vo.Search.SkillSearchQuery;
import com.example.BackendArchitectureLab.Repository.SkillRepository;
import com.example.BackendArchitectureLab.DataAccess.ISkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.specification.SkillSpecification;
import com.example.BackendArchitectureLab.Entity.Skill;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of ISkillDataAccess.
 * Delegates to Spring Data JPA SkillRepository.
 */
@Component
public class SkillDataAccessImpl implements ISkillDataAccess {

    @Autowired
    private SkillRepository skillRepository;

    @Override
    public Skill save(Skill skill) {
        return skillRepository.save(skill);
    }

    @Override
    public boolean exists(Example<Skill> example) {
        return skillRepository.exists(example);
    }

    @Override
    public List<Skill> findAll() {
        return skillRepository.findAll();
    }

    @Override
    public Optional<Skill> findById(UUID key) {
        return skillRepository.findById(key);
    }

    @Override
    public void delete(Skill skill) {
        skillRepository.delete(skill);
    }

    @Override
    public void deleteById(UUID skillId) {
        skillRepository.deleteByIdHard(skillId);
    }
    
    @Override
    public Page<Skill> searchSkills(SkillSearchQuery query) {
        Sort sort = Sort.by(
            "asc".equalsIgnoreCase(query.getSortDir()) 
                ? Sort.Direction.ASC 
                : Sort.Direction.DESC,
            query.getSortBy()
        );
        
        PageRequest pageRequest = PageRequest.of(query.getPage(), query.getSize(), sort);
        
        return skillRepository.findAll(
            SkillSpecification.buildSpecification(query),
            pageRequest
        );
    }
}
