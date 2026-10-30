package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.Dto.Query.UserSearchQuery;
import com.example.BackendArchitectureLab.Repository.UserRepository;
import com.example.BackendArchitectureLab.DataAccess.IUserDataAccess;
import com.example.BackendArchitectureLab.Entity.User;
import com.example.BackendArchitectureLab.DataAccess.specification.UserSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of IUserDataAccess.
 * Delegates to Spring Data JPA UserRepository.
 */
@Component
@RequiredArgsConstructor
public class UserDataAccessImpl implements IUserDataAccess {

    private final UserRepository userRepository;

    private volatile long cachedUnfilteredTotal = -1L;
    private volatile long cachedUnfilteredTotalExpireAt = 0L;

    @Override
    public void save(User user) {
        userRepository.save(user);
    }

    @Override
    public void delete(User user) {
        userRepository.delete(user);
    }

    @Override
    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    public Optional<User> findByEmailWithRoles(String email) {
        return userRepository.findByEmailWithRoles(email);
    }

    @Override
    public Optional<User> findById(UUID key) {
        return userRepository.findById(key);
    }

    @Override
    public Optional<User> findByIdWithRoles(UUID key) {
        return userRepository.findByIdWithRoles(key);
    }

    @Override
    public boolean existsById(UUID key) {
        return userRepository.existsById(key);
    }

    @Override
    public List<User> findAllById(List<UUID> keys) {
        return userRepository.findAllById(keys);
    }

    @Override
    public Page<User> searchUsers(UserSearchQuery query) {
        Sort sort = Sort.by(
            "asc".equalsIgnoreCase(query.getNormalizedSortDir())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC,
            query.getSortBy()
        );

        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(), sort);
        if (isUnfiltered(query)) {
            List<UUID> ids = userRepository.findPagedIds(pageable);
            long now = System.currentTimeMillis();
            long total;
            if (cachedUnfilteredTotal >= 1000L && now < cachedUnfilteredTotalExpireAt) {
                total = cachedUnfilteredTotal;
            } else {
                total = userRepository.count();
                if (total >= 1000L) {
                    cachedUnfilteredTotal = total;
                    cachedUnfilteredTotalExpireAt = now + 5000L;
                }
            }
            if (ids.isEmpty()) {
                return new PageImpl<>(List.of(), pageable, total);
            }
            List<User> enriched = userRepository.findAllByIdsWithRoles(ids);
            Map<UUID, User> enrichedMap = enriched.stream()
                    .collect(Collectors.toMap(User::getId, u -> u, (existing, replacement) -> existing));
            List<User> ordered = ids.stream()
                    .map(enrichedMap::get)
                    .filter(Objects::nonNull)
                    .toList();
            return new PageImpl<>(ordered, pageable, total);
        }

        Page<User> page = userRepository.findAll(UserSpecification.buildSpecification(query), pageable);

        if (page.isEmpty()) {
            return page;
        }

        // 二次批量查詢預載 roles，解決 N+1 問題
        List<UUID> ids = page.getContent().stream().map(User::getId).toList();
        List<User> enriched = userRepository.findAllByIdsWithRoles(ids);
        Map<UUID, User> enrichedMap = enriched.stream()
                .collect(Collectors.toMap(User::getId, u -> u, (existing, replacement) -> existing));
        List<User> ordered = page.getContent().stream()
                .map(u -> enrichedMap.getOrDefault(u.getId(), u))
                .toList();

        return new PageImpl<>(ordered, page.getPageable(), page.getTotalElements());
    }

    private boolean isUnfiltered(UserSearchQuery query) {
        if (query == null) {
            return true;
        }
        return (query.getName() == null || query.getName().isBlank())
                && (query.getEmail() == null || query.getEmail().isBlank())
                && (query.getPhone() == null || query.getPhone().isBlank())
                && query.getDisabled() == null
                && (query.getCreatedBy() == null || query.getCreatedBy().isBlank());
    }

    @Override
    public Page<User> findAllPaged(Pageable pageable) {
        Page<User> page = userRepository.findAll(pageable);
        if (page.isEmpty()) {
            return page;
        }
        List<UUID> ids = page.getContent().stream().map(User::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return page;
        }
        List<User> enriched = userRepository.findAllByIdsWithRoles(ids);
        if (enriched == null || enriched.isEmpty()) {
            return page;
        }
        Map<UUID, User> enrichedMap = enriched.stream()
                .collect(Collectors.toMap(User::getId, u -> u, (existing, replacement) -> existing));
        List<User> ordered = page.getContent().stream()
                .map(u -> enrichedMap.getOrDefault(u.getId(), u))
                .toList();
        return new PageImpl<>(ordered, page.getPageable(), page.getTotalElements());
    }

    @Override
    public List<User> findAllByIdsWithRoles(List<UUID> ids) {
        return userRepository.findAllByIdsWithRoles(ids);
    }
}
