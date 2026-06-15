package com.example.BackendArchitectureLab.DataAccess.impl;

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
        return userJobLinkRepository.findAll().stream()
                .filter(link -> link.getUser().getId().equals(userId))
                .toList();
    }

    @Override
    public List<UserJobLink> findByJobPostingId(UUID jobPostingId) {
        return userJobLinkRepository.findAll().stream()
                .filter(link -> link.getJobPosting().getId().equals(jobPostingId))
                .toList();
    }

    @Override
    public Optional<UserJobLink> findByUserIdAndJobPostingId(UUID userId, UUID jobPostingId) {
        return userJobLinkRepository.findAll().stream()
                .filter(link -> link.getUser().getId().equals(userId)
                        && link.getJobPosting().getId().equals(jobPostingId))
                .findFirst();
    }

    @Override
    public void deleteByUserIdAndJobPostingId(UUID userId, UUID jobPostingId) {
        findByUserIdAndJobPostingId(userId, jobPostingId)
                .ifPresent(userJobLinkRepository::delete);
    }

    @Override
    public boolean existsByUserIdAndJobPostingId(UUID userId, UUID jobPostingId) {
        return userJobLinkRepository.findAll().stream()
                .anyMatch(link -> link.getUser().getId().equals(userId)
                        && link.getJobPosting().getId().equals(jobPostingId));
    }
}
