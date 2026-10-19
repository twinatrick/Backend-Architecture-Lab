package com.example.BackendArchitectureLab.DataAccess;

import com.example.BackendArchitectureLab.Entity.UserJobLink;
import com.example.BackendArchitectureLab.Dto.Query.UserJobLinkSearchQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IUserJobLinkDataAccess {

    UserJobLink save(UserJobLink userJobLink);

    List<UserJobLink> findAll();

    Page<UserJobLink> findAllPaged(Pageable pageable);

    Optional<UserJobLink> findById(UUID id);

    List<UserJobLink> findAllById(List<UUID> ids);

    boolean existsById(UUID id);

    void deleteById(UUID id);

    List<UserJobLink> findByUserId(UUID userId);

    List<UserJobLink> findByJobPostingId(UUID jobPostingId);

    Optional<UserJobLink> findByUserIdAndJobPostingId(UUID userId, UUID jobPostingId);

    void deleteByUserIdAndJobPostingId(UUID userId, UUID jobPostingId);

    boolean existsByUserIdAndJobPostingId(UUID userId, UUID jobPostingId);

    /**
     * 批次查詢含 jobPosting 與 company 的 UserJobLink 清單（用於解決二次批量查詢 N+1 問題）
     *
     * @param ids UserJobLink UUID 清單
     * @return 含關聯資料的清單
     */
    List<UserJobLink> findAllByIdsWithJobPosting(List<UUID> ids);

    Page<UserJobLink> searchUserJobLinks(UserJobLinkSearchQuery query);
}
