package com.example.BackendArchitectureLab.Repository;

import com.example.BackendArchitectureLab.Entity.UserJobLink;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserJobLinkRepository extends JpaRepository<UserJobLink, UUID>, JpaSpecificationExecutor<UserJobLink> {

    @Override
    @EntityGraph(attributePaths = {"jobPosting", "jobPosting.company"})
    List<UserJobLink> findAll();

    @Override
    @EntityGraph(attributePaths = {"jobPosting", "jobPosting.company"})
    List<UserJobLink> findAllById(Iterable<UUID> ids);

    @Override
    @EntityGraph(attributePaths = {"jobPosting", "jobPosting.company"})
    Page<UserJobLink> findAll(Pageable pageable);

    // 移除原本帶 @EntityGraph 的 findAll(Specification, Pageable)，避免 in-memory pagination 問題
    // searchUserJobLinks 改為二次批量查詢模式

    @Query("SELECT u FROM UserJobLink u LEFT JOIN FETCH u.jobPosting jp LEFT JOIN FETCH jp.company WHERE u.id IN :ids")
    List<UserJobLink> findAllByIdsWithJobPosting(@Param("ids") List<UUID> ids);

    @Override
    @EntityGraph(attributePaths = {"jobPosting", "jobPosting.company"})
    Optional<UserJobLink> findById(UUID id);

    @EntityGraph(attributePaths = {"jobPosting", "jobPosting.company"})
    List<UserJobLink> findByUserId(UUID userId);

    @EntityGraph(attributePaths = {"jobPosting", "jobPosting.company"})
    List<UserJobLink> findByJobPostingId(UUID jobPostingId);

    @EntityGraph(attributePaths = {"jobPosting", "jobPosting.company"})
    Optional<UserJobLink> findByUserIdAndJobPostingId(UUID userId, UUID jobPostingId);

    boolean existsByUserIdAndJobPostingId(UUID userId, UUID jobPostingId);
}
