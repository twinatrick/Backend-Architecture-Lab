package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.DataAccess.IUserJobLinkDataAccess;
import com.example.BackendArchitectureLab.DataAccess.specification.UserJobLinkSpecification;
import com.example.BackendArchitectureLab.Entity.UserJobLink;
import com.example.BackendArchitectureLab.Repository.UserJobLinkRepository;
import com.example.BackendArchitectureLab.Vo.Search.UserJobLinkSearchQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UserJobLinkDataAccessImpl implements IUserJobLinkDataAccess {

    private final UserJobLinkRepository userJobLinkRepository;

    @Override
    public UserJobLink save(UserJobLink userJobLink) {
        return userJobLinkRepository.save(userJobLink);
    }

    @Override
    public List<UserJobLink> findAll() {
        return userJobLinkRepository.findAll();
    }

    @Override
    public Page<UserJobLink> findAllPaged(Pageable pageable) {
        return userJobLinkRepository.findAll(pageable);
    }

    @Override
    public Optional<UserJobLink> findById(UUID id) {
        return userJobLinkRepository.findById(id);
    }

    @Override
    public List<UserJobLink> findAllById(List<UUID> ids) {
        return userJobLinkRepository.findAllById(ids);
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

    @Override
    public List<UserJobLink> findAllByIdsWithJobPosting(List<UUID> ids) {
        return userJobLinkRepository.findAllByIdsWithJobPosting(ids);
    }

    @Override
    public Page<UserJobLink> searchUserJobLinks(UserJobLinkSearchQuery query) {
        Sort sort = Sort.by(
                "asc".equalsIgnoreCase(query.getNormalizedSortDir())
                        ? Sort.Direction.ASC : Sort.Direction.DESC,
                query.getSortBy()
        );
        PageRequest pageRequest = PageRequest.of(query.getPage(), query.getSize(), sort);

        // 第一次查詢：分頁取得 ID（無 EntityGraph，避免 in-memory pagination）
        Page<UserJobLink> page = userJobLinkRepository.findAll(UserJobLinkSpecification.buildSpecification(query), pageRequest);

        if (page.isEmpty()) {
            return page;
        }

        // 第二次查詢：批量載入含 jobPosting + company 的完整資料
        List<UUID> ids = page.getContent().stream().map(UserJobLink::getId).toList();
        Map<UUID, UserJobLink> linkMap = userJobLinkRepository.findAllByIdsWithJobPosting(ids)
                .stream().collect(Collectors.toMap(UserJobLink::getId, l -> l));

        List<UserJobLink> enriched = page.getContent().stream()
                .map(l -> linkMap.getOrDefault(l.getId(), l))
                .toList();

        return new PageImpl<>(enriched, pageRequest, page.getTotalElements());
    }
}
