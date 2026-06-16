package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.UserJobLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserJobLinkRepository extends JpaRepository<UserJobLink, UUID> {
    List<UserJobLink> findByUserId(UUID userId);

    List<UserJobLink> findByJobPostingId(UUID jobPostingId);

    Optional<UserJobLink> findByUserIdAndJobPostingId(UUID userId, UUID jobPostingId);

    boolean existsByUserIdAndJobPostingId(UUID userId, UUID jobPostingId);
}
