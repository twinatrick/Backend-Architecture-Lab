package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.Dto.Vo.Search.SkillLevelSearchQuery;
import com.example.BackendArchitectureLab.Repository.SkillLevelRepository;
import com.example.BackendArchitectureLab.DataAccess.ISkillLevelDataAccess;
import com.example.BackendArchitectureLab.DataAccess.specification.SkillLevelSpecification;
import com.example.BackendArchitectureLab.Entity.SkillLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class SkillLevelDataAccessImpl implements ISkillLevelDataAccess {

    @Autowired
    private SkillLevelRepository skillLevelRepository;

    @Override
    public SkillLevel save(SkillLevel skillLevel) {
        return skillLevelRepository.save(skillLevel);
    }

    @Override
    public Optional<SkillLevel> findById(UUID id) {
        return skillLevelRepository.findById(id);
    }

    @Override
    public List<SkillLevel> findBySkillIdOrderByLevelValueAsc(UUID skillId) {
        return skillLevelRepository.findBySkillIdOrderByLevelValueAsc(skillId);
    }

    @Override
    public boolean existsBySkillIdAndLevelValue(UUID skillId, Integer levelValue) {
        return skillLevelRepository.existsBySkillIdAndLevelValue(skillId, levelValue);
    }

    @Override
    public void delete(SkillLevel skillLevel) {
        skillLevelRepository.delete(skillLevel);
    }

    @Override
    public void deleteBySkillId(UUID skillId) {
        skillLevelRepository.deleteBySkillId(skillId);
    }
    
    @Override
    public Page<SkillLevel> searchSkillLevels(SkillLevelSearchQuery query) {
        Sort sort = Sort.by(
            "asc".equalsIgnoreCase(query.getSortDir()) 
                ? Sort.Direction.ASC 
                : Sort.Direction.DESC,
            query.getSortBy()
        );
        
        PageRequest pageRequest = PageRequest.of(query.getPage(), query.getSize(), sort);
        
        return skillLevelRepository.findAll(
            SkillLevelSpecification.buildSpecification(query),
            pageRequest
        );
    }
}
