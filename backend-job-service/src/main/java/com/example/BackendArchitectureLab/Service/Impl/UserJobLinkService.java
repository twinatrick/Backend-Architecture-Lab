package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Vo.Cache.CacheListWrapper;
import org.springframework.context.annotation.Lazy;
import com.example.BackendArchitectureLab.DataAccess.IJobPostingDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserJobLinkDataAccess;
import com.example.BackendArchitectureLab.Vo.UserJobLinkVo;
import com.example.BackendArchitectureLab.Entity.JobPosting;
import com.example.BackendArchitectureLab.Entity.UserJobLink;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Feign.UserServiceFeignClient;
import com.example.BackendArchitectureLab.Mapper.UserJobLinkMapper;
import com.example.BackendArchitectureLab.Service.IUserJobLinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserJobLinkService implements IUserJobLinkService {

    private final IUserJobLinkDataAccess userJobLinkDataAccess;
    private final IJobPostingDataAccess jobPostingDataAccess;
    private final UserJobLinkMapper userJobLinkMapper;
    private final CacheManager cacheManager;
    private final UserServiceFeignClient userServiceFeignClient;

    @Lazy
    private final IUserJobLinkService self;

    @Override
    @Transactional
    @Caching(put = {
        @CachePut(value = "userJobLinks", key = "#result.id")
    }, evict = {
        @CacheEvict(value = "userJobLinks", key = "'byuser:' + #userJobLinkVo.userId"),
        @CacheEvict(value = "userJobLinks", key = "'byjob:' + #userJobLinkVo.jobPostingId"),
        @CacheEvict(value = "userJobLinks", key = "'currentuser:' + #userJobLinkVo.userId")
    })
    public UserJobLinkVo createUserJobLink(UserJobLinkVo userJobLinkVo) {
        UserJobLink link = new UserJobLink();

        if (userJobLinkVo.getUserId() != null) {
            UUID userUuid = UUID.fromString(userJobLinkVo.getUserId());
            if (!userServiceFeignClient.existsUserById(userUuid)) {
                throw new IllegalArgumentException("User not found");
            }
            link.setUserId(userUuid);
        }

        if (userJobLinkVo.getJobPostingId() != null) {
            JobPosting jobPosting = jobPostingDataAccess.findById(UUID.fromString(userJobLinkVo.getJobPostingId()))
                    .orElseThrow(() -> new IllegalArgumentException("Job posting not found"));
            link.setJobPosting(jobPosting);
        }

        link.setUserNotes(userJobLinkVo.getUserNotes());
        link = userJobLinkDataAccess.save(link);
        return userJobLinkMapper.toVo(link);
    }

    @Override
    public List<UserJobLinkVo> getAllUserJobLinks() {
        return self.getAllUserJobLinksCache().getData();
    }

    @Override
    @Cacheable(value = "userJobLinks", key = "'all'", sync = true)
    public CacheListWrapper<UserJobLinkVo> getAllUserJobLinksCache() {
        List<UserJobLinkVo> list = userJobLinkDataAccess.findAll().stream()
                .map(userJobLinkMapper::toVo)
                .toList();
        return new CacheListWrapper<>(list);
    }

    @Override
    @Cacheable(value = "userJobLinks", key = "#id", sync = true)
    public UserJobLinkVo getUserJobLinkById(String id) {
        UUID uuid = mapUuid(id);
        if (uuid == null) {
            throw new IllegalArgumentException("ID must not be null");
        }
        UserJobLink link = userJobLinkDataAccess.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("User job link not found"));
        return userJobLinkMapper.toVo(link);
    }

    @Override
    @Transactional
    public void deleteUserJobLink(String id) {
        UUID uuid = mapUuid(id);
        if (uuid == null) {
            throw new IllegalArgumentException("ID must not be null");
        }
        UserJobLink link = userJobLinkDataAccess.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("User job link not found"));
        String userId = link.getUserId().toString();
        String jobPostingId = link.getJobPosting().getId().toString();
        userJobLinkDataAccess.deleteById(uuid);
        Cache cache = cacheManager.getCache("userJobLinks");
        if (cache != null) {
            cache.evict(id);
            cache.evict("byuser:" + userId);
            cache.evict("byjob:" + jobPostingId);
            cache.evict("currentuser:" + userId);
        }
    }

    @Override
    public List<UserJobLinkVo> getUserJobLinksByUserId(String userId) {
        return self.getUserJobLinksByUserIdCache(userId).getData();
    }

    @Override
    @Cacheable(value = "userJobLinks", key = "'byuser:' + #userId", sync = true)
    public CacheListWrapper<UserJobLinkVo> getUserJobLinksByUserIdCache(String userId) {
        UUID uuid = mapUuid(userId);
        if (uuid == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }
        List<UserJobLinkVo> list = userJobLinkDataAccess.findByUserId(uuid).stream()
                .map(userJobLinkMapper::toVo)
                .toList();
        return new CacheListWrapper<>(list);
    }

    @Override
    public List<UserJobLinkVo> getUserJobLinksByJobPostingId(String jobPostingId) {
        return self.getUserJobLinksByJobPostingIdCache(jobPostingId).getData();
    }

    @Override
    @Cacheable(value = "userJobLinks", key = "'byjob:' + #jobPostingId", sync = true)
    public CacheListWrapper<UserJobLinkVo> getUserJobLinksByJobPostingIdCache(String jobPostingId) {
        UUID uuid = mapUuid(jobPostingId);
        if (uuid == null) {
            throw new IllegalArgumentException("Job posting ID must not be null");
        }
        List<UserJobLinkVo> list = userJobLinkDataAccess.findByJobPostingId(uuid).stream()
                .map(userJobLinkMapper::toVo)
                .toList();
        return new CacheListWrapper<>(list);
    }

    @Override
    @Transactional
    @Caching(put = {
        @CachePut(value = "userJobLinks", key = "#result.id")
    }, evict = {
        @CacheEvict(value = "userJobLinks", key = "'byuser:' + #userJobLinkVo.userId"),
        @CacheEvict(value = "userJobLinks", key = "'byjob:' + #userJobLinkVo.jobPostingId"),
        @CacheEvict(value = "userJobLinks", key = "'currentuser:' + #userJobLinkVo.userId")
    })
    public UserJobLinkVo updateUserJobLink(UserJobLinkVo userJobLinkVo) {
        if (userJobLinkVo.getId() == null || userJobLinkVo.getId().isBlank()) {
            throw new IllegalArgumentException("ID must not be null");
        }
        UUID uuid = UUID.fromString(userJobLinkVo.getId());
        UserJobLink link = userJobLinkDataAccess.findById(uuid)
                .orElseThrow(() -> new AppException("NOT_FOUND", "使用者職缺連結不存在", 404));
        if (userJobLinkVo.getUserNotes() != null) {
            link.setUserNotes(userJobLinkVo.getUserNotes());
        }
        if (userJobLinkVo.getGeminiFeedback() != null) {
            link.setGeminiFeedback(userJobLinkVo.getGeminiFeedback());
        }
        link = userJobLinkDataAccess.save(link);
        return userJobLinkMapper.toVo(link);
    }

    @Override
    @Transactional
    @Caching(put = {
        @CachePut(value = "userJobLinks", key = "#result.id")
    }, evict = {
        @CacheEvict(value = "userJobLinks", key = "'byuser:' + #currentUserId"),
        @CacheEvict(value = "userJobLinks", key = "'byjob:' + #jobPostingId"),
        @CacheEvict(value = "userJobLinks", key = "'currentuser:' + #currentUserId")
    })
    public UserJobLinkVo addJobToCurrentUser(String currentUserId, String jobPostingId) {
        UUID userUuid = mapUuid(currentUserId);
        UUID jobUuid = mapUuid(jobPostingId);
        if (userUuid == null || jobUuid == null) {
            throw new IllegalArgumentException("User ID and Job Posting ID must not be null");
        }
        if (userJobLinkDataAccess.existsByUserIdAndJobPostingId(userUuid, jobUuid)) {
            throw new IllegalArgumentException("Job already bound to current user");
        }
        if (!userServiceFeignClient.existsUserById(userUuid)) {
            throw new IllegalArgumentException("User not found");
        }
        JobPosting jobPosting = jobPostingDataAccess.findById(jobUuid)
                .orElseThrow(() -> new IllegalArgumentException("Job posting not found"));
        UserJobLink link = new UserJobLink();
        link.setUserId(userUuid);
        link.setJobPosting(jobPosting);
        link = userJobLinkDataAccess.save(link);
        return userJobLinkMapper.toVo(link);
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "userJobLinks", key = "'byuser:' + #currentUserId"),
        @CacheEvict(value = "userJobLinks", key = "'byjob:' + #jobPostingId"),
        @CacheEvict(value = "userJobLinks", key = "'currentuser:' + #currentUserId")
    })
    public void removeJobFromCurrentUser(String currentUserId, String jobPostingId) {
        UUID userUuid = mapUuid(currentUserId);
        UUID jobUuid = mapUuid(jobPostingId);
        if (userUuid == null || jobUuid == null) {
            throw new IllegalArgumentException("User ID and Job Posting ID must not be null");
        }
        if (!userJobLinkDataAccess.existsByUserIdAndJobPostingId(userUuid, jobUuid)) {
            throw new IllegalArgumentException("Job binding not found");
        }
        userJobLinkDataAccess.deleteByUserIdAndJobPostingId(userUuid, jobUuid);
    }

    @Override
    public List<UserJobLinkVo> getCurrentUserJobLinks(String currentUserId) {
        return self.getCurrentUserJobLinksCache(currentUserId).getData();
    }

    @Override
    @Cacheable(value = "userJobLinks", key = "'currentuser:' + #currentUserId", sync = true)
    public CacheListWrapper<UserJobLinkVo> getCurrentUserJobLinksCache(String currentUserId) {
        return self.getUserJobLinksByUserIdCache(currentUserId);
    }

    private UUID mapUuid(String id) {
        return id == null || id.isBlank() ? null : UUID.fromString(id);
    }
}
