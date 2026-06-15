package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Entity.UserJobLink;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IUserJobLinkDataAccess {

    UserJobLink save(UserJobLink userJobLink);

    List<UserJobLink> findAll();

    Optional<UserJobLink> findById(UUID id);

    boolean existsById(UUID id);

    void deleteById(UUID id);

    List<UserJobLink> findByUserId(UUID userId);

    List<UserJobLink> findByJobPostingId(UUID jobPostingId);

    Optional<UserJobLink> findByUserIdAndJobPostingId(UUID userId, UUID jobPostingId);

    void deleteByUserIdAndJobPostingId(UUID userId, UUID jobPostingId);

    boolean existsByUserIdAndJobPostingId(UUID userId, UUID jobPostingId);
}
