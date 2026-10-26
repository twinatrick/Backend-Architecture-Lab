package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.DataAccess.IUserJobLinkDataAccess;
import com.example.BackendArchitectureLab.DataAccess.specification.UserJobLinkSpecification;
import com.example.BackendArchitectureLab.Entity.UserJobLink;
import com.example.BackendArchitectureLab.Repository.UserJobLinkRepository;
import com.example.BackendArchitectureLab.Dto.Query.UserJobLinkSearchQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UserJobLinkDataAccessImpl implements IUserJobLinkDataAccess {

    private static final long UNFILTERED_COUNT_TTL_NANOS = 5_000_000_000L;
    private static final long COUNT_CACHE_MIN_ROWS = 1_000L;

    private final UserJobLinkRepository userJobLinkRepository;

    private volatile long cachedUnfilteredCount = -1L;
    private volatile long cachedCountTimestampNanos = 0L;

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
    @Transactional
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

        final List<UUID> ids;
        final long totalElements;

        if (isUnfilteredQuery(query)) {
            ids = userJobLinkRepository.findPagedIds(pageRequest);
            totalElements = getUnfilteredTotalCount(ids.size(), pageRequest);
            if (ids.isEmpty()) {
                return new PageImpl<>(Collections.emptyList(), pageRequest, totalElements);
            }
        } else {
            // 第一次查詢：分頁取得 ID（無 EntityGraph，避免 in-memory pagination）
            Page<UserJobLink> page = userJobLinkRepository.findAll(UserJobLinkSpecification.buildSpecification(query), pageRequest);
            if (page.isEmpty()) {
                return page;
            }
            ids = page.getContent().stream().map(UserJobLink::getId).toList();
            totalElements = page.getTotalElements();
        }

        // 第二次查詢：批量載入含 jobPosting + company 的完整資料
        Map<UUID, UserJobLink> linkMap = userJobLinkRepository.findAllByIdsWithJobPosting(ids)
                .stream().collect(Collectors.toMap(UserJobLink::getId, l -> l, (existing, replacement) -> existing));

        List<UserJobLink> enriched = ids.stream()
                .map(linkMap::get)
                .filter(l -> l != null)
                .toList();

        return new PageImpl<>(enriched, pageRequest, totalElements);
    }

    private boolean isUnfilteredQuery(UserJobLinkSearchQuery query) {
        if (query == null) {
            return true;
        }
        return isBlank(query.getUserEmail())
                && isBlank(query.getJobTitle())
                && isBlank(query.getCompanyName())
                && isBlank(query.getUserId())
                && isBlank(query.getJobPostingId())
                && isBlank(query.getCreatedBy());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private long getUnfilteredTotalCount(int fetchedSize, Pageable pageable) {
        if (pageable.getOffset() == 0 && fetchedSize < pageable.getPageSize()) {
            return fetchedSize;
        }
        long now = System.nanoTime();
        long cached = cachedUnfilteredCount;
        if (cached >= COUNT_CACHE_MIN_ROWS && (now - cachedCountTimestampNanos) < UNFILTERED_COUNT_TTL_NANOS) {
            return cached;
        }
        long exactCount = userJobLinkRepository.count();
        if (exactCount >= COUNT_CACHE_MIN_ROWS) {
            cachedUnfilteredCount = exactCount;
            cachedCountTimestampNanos = now;
        }
        return exactCount;
    }
}
