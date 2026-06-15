package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.DataAccess.IJobPostingDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserJobLinkDataAccess;
import com.example.BackendArchitectureLab.Dto.Vo.UserJobLinkVo;
import com.example.BackendArchitectureLab.Entity.JobPosting;
import com.example.BackendArchitectureLab.Entity.User;
import com.example.BackendArchitectureLab.Entity.UserJobLink;
import com.example.BackendArchitectureLab.Feign.UserServiceFeignClient;
import com.example.BackendArchitectureLab.Mapper.UserJobLinkMapper;
import com.example.BackendArchitectureLab.Service.IUserJobLinkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
public class UserJobLinkService implements IUserJobLinkService {

    @Autowired
    private IUserJobLinkDataAccess userJobLinkDataAccess;
    @Autowired
    private IJobPostingDataAccess jobPostingDataAccess;
    @Autowired
    private UserJobLinkMapper userJobLinkMapper;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private UserServiceFeignClient userServiceFeignClient;

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
            User user = new User();
            user.setId(userUuid);
            link.setUser(user);
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
    @Cacheable(value = "userJobLinks", sync = true)
    public List<UserJobLinkVo> getAllUserJobLinks() {
        return userJobLinkDataAccess.findAll().stream()
                .map(userJobLinkMapper::toVo)
                .toList();
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
        String userId = link.getUser().getId().toString();
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
    @Cacheable(value = "userJobLinks", key = "'byuser:' + #userId", sync = true)
    public List<UserJobLinkVo> getUserJobLinksByUserId(String userId) {
        UUID uuid = mapUuid(userId);
        if (uuid == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }
        return userJobLinkDataAccess.findByUserId(uuid).stream()
                .map(userJobLinkMapper::toVo)
                .toList();
    }

    @Override
    @Cacheable(value = "userJobLinks", key = "'byjob:' + #jobPostingId", sync = true)
    public List<UserJobLinkVo> getUserJobLinksByJobPostingId(String jobPostingId) {
        UUID uuid = mapUuid(jobPostingId);
        if (uuid == null) {
            throw new IllegalArgumentException("Job posting ID must not be null");
        }
        return userJobLinkDataAccess.findByJobPostingId(uuid).stream()
                .map(userJobLinkMapper::toVo)
                .toList();
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
        User user = new User();
        user.setId(userUuid);
        link.setUser(user);
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
    @Cacheable(value = "userJobLinks", key = "'currentuser:' + #currentUserId", sync = true)
    public List<UserJobLinkVo> getCurrentUserJobLinks(String currentUserId) {
        return getUserJobLinksByUserId(currentUserId);
    }

    private UUID mapUuid(String id) {
        return id == null || id.isBlank() ? null : UUID.fromString(id);
    }
}
