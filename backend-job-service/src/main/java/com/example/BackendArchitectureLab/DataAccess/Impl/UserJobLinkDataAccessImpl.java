package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.DataAccess.IUserJobLinkDataAccess;
import com.example.BackendArchitectureLab.Entity.UserJobLink;
import com.example.BackendArchitectureLab.Repository.UserJobLinkRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class UserJobLinkDataAccessImpl implements IUserJobLinkDataAccess {

    @Autowired
    private UserJobLinkRepository userJobLinkRepository;

    @Override
    public UserJobLink save(UserJobLink userJobLink) {
        return userJobLinkRepository.save(userJobLink);
    }

    @Override
    public List<UserJobLink> findAll() {
        return userJobLinkRepository.findAll();
    }

    @Override
    public Optional<UserJobLink> findById(UUID id) {
        return userJobLinkRepository.findById(id);
    }

    @Override
    public boolean existsById(UUID id) {
        return userJobLinkRepository.existsById(id);
    }

    @Override
    public void deleteById(UUID id) {
        userJobLinkRepository.deleteById(id);
    }

    @Override
    public List<UserJobLink> findByUserId(UUID userId) {
        return userJobLinkRepository.findByUserId(userId);
    }

    @Override
    public List<UserJobLink> findByJobPostingId(UUID jobPostingId) {
        return userJobLinkRepository.findByJobPostingId(jobPostingId);
    }

    @Override
    public Optional<UserJobLink> findByUserIdAndJobPostingId(UUID userId, UUID jobPostingId) {
        return userJobLinkRepository.findByUserIdAndJobPostingId(userId, jobPostingId);
    }

    @Override
    public void deleteByUserIdAndJobPostingId(UUID userId, UUID jobPostingId) {
        findByUserIdAndJobPostingId(userId, jobPostingId)
                .ifPresent(userJobLinkRepository::delete);
    }

    @Override
    public boolean existsByUserIdAndJobPostingId(UUID userId, UUID jobPostingId) {
        return userJobLinkRepository.existsByUserIdAndJobPostingId(userId, jobPostingId);
    }
}
