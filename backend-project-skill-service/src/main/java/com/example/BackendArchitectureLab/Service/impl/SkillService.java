package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Dto.Vo.CurrentUserSkillVo;
import com.example.BackendArchitectureLab.Dto.Vo.PersonalSkillRequest;
import com.example.BackendArchitectureLab.Dto.Vo.Search.SkillLevelSearchQuery;
import com.example.BackendArchitectureLab.Dto.Vo.Search.SkillSearchQuery;
import com.example.BackendArchitectureLab.Dto.Vo.SkillLevelVo;
import com.example.BackendArchitectureLab.Dto.Vo.SkillVo;
import com.example.BackendArchitectureLab.Dto.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Dto.Vo.UserVo;
import com.example.BackendArchitectureLab.Entity.Project;
import com.example.BackendArchitectureLab.Entity.ProjectSkill;
import com.example.BackendArchitectureLab.Entity.Skill;
import com.example.BackendArchitectureLab.Entity.SkillLevel;
import com.example.BackendArchitectureLab.Entity.User;
import com.example.BackendArchitectureLab.Entity.UserProject;
import com.example.BackendArchitectureLab.Entity.UserSkill;
import com.example.BackendArchitectureLab.Service.ISkillService;
import com.example.BackendArchitectureLab.Util.SortFieldValidator;
import com.example.BackendArchitectureLab.DataAccess.IProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IProjectSkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.ISkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.ISkillLevelDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserSkillDataAccess;
import com.example.BackendArchitectureLab.Feign.UserServiceFeignClient;
import com.example.BackendArchitectureLab.Mapper.SkillMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SkillService implements ISkillService {
    @Autowired
    private ISkillDataAccess skillDataAccess;
    @Autowired
    private IProjectDataAccess projectDataAccess;
    @Autowired
    private ISkillLevelDataAccess skillLevelDataAccess;
    @Autowired
    private IUserSkillDataAccess userSkillDataAccess;
    @Autowired
    private IUserProjectDataAccess userProjectDataAccess;
    @Autowired
    private IProjectSkillDataAccess projectSkillDataAccess;
    @Autowired
    private SkillMapper skillMapper;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private UserServiceFeignClient userServiceFeignClient;

    @Transactional
    @Override
    @Caching(put = {
        @CachePut(value = "skills", key = "#result.id")
    }, evict = {
        @CacheEvict(value = "skills", key = "'all'")
    })
    public SkillVo addSkill(SkillVo skillVo) {
        Skill skill = skillMapper.toEntity(skillVo);
        if (skill.getId() != null) {
            throw new IllegalArgumentException("Key must be null");
        } else if (skill.getName() == null) {
            throw new IllegalArgumentException("Name must not be null");
        }
        Example<Skill> example = Example.of(skill);
        if (skillDataAccess.exists(example)) {
            throw new IllegalArgumentException("Name already exists");
        }

        Skill savedSkill = skillDataAccess.save(skill);
        
        // 處理使用者綁定（如果提供了 userIds）
        if (skillVo.getUserIds() != null && !skillVo.getUserIds().isEmpty()) {
            bindUsersToSkill(savedSkill.getId(), skillVo.getUserIds(), skillVo.getSkillLevelId());
        }
        
        return skillMapper.toVo(savedSkill);
    }

    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "skills", key = "#skillVo.id"),
        @CacheEvict(value = "skills", key = "'all'")
    })
    public void updateSkill(SkillVo skillVo) {
        Skill skill = skillMapper.toEntity(skillVo);
        if (skill.getId() == null) {
            throw new IllegalArgumentException("Key must not be null");
        } else if (skill.getName() == null) {
            throw new IllegalArgumentException("Name must not be null");
        }
        skillDataAccess.save(skill);
        
        // 處理使用者重新綁定（如果提供了 userIds）
        if (skillVo.getUserIds() != null) {
            // 先刪除現有綁定
            List<UserSkill> existingBindings = userSkillDataAccess.findBySkillId(skill.getId());
            existingBindings.forEach(userSkill -> 
                userSkillDataAccess.deleteByUserIdAndSkillId(userSkill.getUser().getId(), skill.getId())
            );
            
            // 重新綁定（如果 userIds 不為空）
            if (!skillVo.getUserIds().isEmpty()) {
                bindUsersToSkill(skill.getId(), skillVo.getUserIds(), skillVo.getSkillLevelId());
            }
        }
    }
    
    /**
     * 綁定多個使用者到技能
     * 
     * @param skillId 技能 ID
     * @param userIds 使用者 ID 列表
     * @param skillLevelId 技能等級 ID（可選）
     */
    private void bindUsersToSkill(UUID skillId, List<String> userIds, String skillLevelId) {
        Skill skill = skillDataAccess.findById(skillId)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found"));
        
        // 取得技能等級（如果提供）
        SkillLevel skillLevel = null;
        if (skillLevelId != null && !skillLevelId.isBlank()) {
            UUID levelId = UUID.fromString(skillLevelId);
            skillLevel = skillLevelDataAccess.findById(levelId)
                    .orElseThrow(() -> new IllegalArgumentException("Skill level not found"));
            
            // 驗證技能等級是否屬於該技能
            if (!skillLevel.getSkill().getId().equals(skillId)) {
                throw new IllegalArgumentException("Skill level does not belong to this skill");
            }
        } else {
            // 如果沒有提供技能等級，使用該技能的第一個等級
            List<SkillLevel> levels = skillLevelDataAccess.findBySkillIdOrderByLevelValueAsc(skillId);
            if (!levels.isEmpty()) {
                skillLevel = levels.get(0);
            }
        }
        
        Set<UUID> targetUserIds = userIds.stream()
                .map(UUID::fromString)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 綁定每個使用者
        for (UUID userId : targetUserIds) {
            String userIdStr = userId.toString();
            
            // 驗證使用者是否存在
            if (!userServiceFeignClient.existsUserById(userId)) {
                throw new IllegalArgumentException("User not found: " + userIdStr);
            }
            
            // 檢查是否已存在綁定
            if (!userSkillDataAccess.existsByUserIdAndSkillId(userId, skillId)) {
                User user = new User();
                user.setId(userId);
                
                UserSkill userSkill = new UserSkill();
                userSkill.setUser(user);
                userSkill.setSkill(skill);
                userSkill.setSkillLevel(skillLevel);
                userSkillDataAccess.save(userSkill);
            }
        }
    }

    @Override
    @Cacheable(value = "skills", key = "'all'", sync = true)
    public List<SkillVo> getSkill() {
        return skillDataAccess.findAll().stream().map(skillMapper::toVo).toList();
    }

    @Override
    @Caching(evict = {
        @CacheEvict(value = "skillLevels", key = "#skillLevelVo.skillId"),
        @CacheEvict(value = "skills", key = "'all'")
    })
    public SkillLevelVo addSkillLevel(SkillLevelVo skillLevelVo) {
        if (skillLevelVo.getId() != null && !skillLevelVo.getId().isBlank()) {
            throw new IllegalArgumentException("Key must be null");
        }
        UUID skillId = mapUuid(skillLevelVo.getSkillId());
        if (skillId == null) {
            throw new IllegalArgumentException("Skill key must not be null");
        }
        validateLevelInput(skillLevelVo);

        Skill skill = skillDataAccess.findById(skillId)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found"));
        if (skillLevelDataAccess.existsBySkillIdAndLevelValue(skillId, skillLevelVo.getLevelValue())) {
            throw new IllegalArgumentException("Skill level value already exists");
        }


        SkillLevel skillLevel = new SkillLevel();
        skillLevel.setSkill(skill);
        skillLevel.setLevelValue(skillLevelVo.getLevelValue());
        skillLevel.setTitle(skillLevelVo.getTitle());
        skillLevel.setDescription(skillLevelVo.getDescription());
        skillLevel.setUserSkills(new ArrayList<>());
        return mapSkillLevelVo(skillLevelDataAccess.save(skillLevel));
    }

    @Override
    @Caching(evict = {
        @CacheEvict(value = "skillLevels", key = "#skillLevelVo.skillId"),
        @CacheEvict(value = "skills", key = "'all'")
    })
    public void updateSkillLevel(SkillLevelVo skillLevelVo) {
        UUID skillLevelId = mapUuid(skillLevelVo.getId());
        if (skillLevelId == null) {
            throw new IllegalArgumentException("Key must not be null");
        }
        validateLevelInput(skillLevelVo);

        SkillLevel skillLevel = skillLevelDataAccess.findById(skillLevelId)
                .orElseThrow(() -> new IllegalArgumentException("Skill level not found"));

        List<SkillLevel> existingLevels = skillLevelDataAccess.findBySkillIdOrderByLevelValueAsc(skillLevel.getSkill().getId());
        boolean levelValueUsed = existingLevels.stream().anyMatch(item ->
                !item.getId().equals(skillLevel.getId()) && item.getLevelValue().equals(skillLevelVo.getLevelValue())
        );
        if (levelValueUsed) {
            throw new IllegalArgumentException("Skill level value already exists");
        }

        skillLevel.setLevelValue(skillLevelVo.getLevelValue());
        skillLevel.setTitle(skillLevelVo.getTitle());
        skillLevel.setDescription(skillLevelVo.getDescription());
        skillLevelDataAccess.save(skillLevel);
    }

    @Override
    @Cacheable(value = "skillLevels", key = "#skillId", sync = true)
    public List<SkillLevelVo> getSkillLevels(String skillId) {
        UUID skillUuid = mapUuid(skillId);
        if (skillUuid == null) {
            throw new IllegalArgumentException("Skill key must not be null");
        }
        skillDataAccess.findById(skillUuid).orElseThrow(() -> new IllegalArgumentException("Skill not found"));
        return skillLevelDataAccess.findBySkillIdOrderByLevelValueAsc(skillUuid)
                .stream()
                .map(this::mapSkillLevelVo)
                .toList();
    }

    @Override
    public void deleteSkillLevel(String skillLevelId) {
        UUID skillLevelUuid = mapUuid(skillLevelId);
        if (skillLevelUuid == null) {
            throw new IllegalArgumentException("Skill level key must not be null");
        }
        if (userSkillDataAccess.existsBySkillLevelId(skillLevelUuid)
                || projectSkillDataAccess.existsBySkillLevelId(skillLevelUuid)) {
            throw new IllegalArgumentException("Skill level is already in use");
        }
        SkillLevel skillLevel = skillLevelDataAccess.findById(skillLevelUuid)
                .orElseThrow(() -> new IllegalArgumentException("Skill level not found"));
        String skillId = skillLevel.getSkill().getId().toString();
        skillLevelDataAccess.delete(skillLevel);
        Cache cache = cacheManager.getCache("skillLevels");
        if (cache != null) {
            cache.evict(skillId);
        }
        Cache skillsCache = cacheManager.getCache("skills");
        if (skillsCache != null) {
            skillsCache.evict("all");
        }
    }

    @Override
    @Transactional
    public void bindUserSkill(String userId, String skillId, String skillLevelId) {
        UUID userUuid = mapUuid(userId);
        UUID skillUuid = mapUuid(skillId);
        UUID skillLevelUuid = mapUuid(skillLevelId);
        if (userUuid == null || skillUuid == null || skillLevelUuid == null) {
            throw new IllegalArgumentException("Key must not be null");
        }

        if (!userServiceFeignClient.existsUserById(userUuid)) {
            throw new IllegalArgumentException("User not found");
        }
        Skill skill = skillDataAccess.findById(skillUuid)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found"));
        SkillLevel skillLevel = skillLevelDataAccess.findById(skillLevelUuid)
                .orElseThrow(() -> new IllegalArgumentException("Skill level not found"));
        validateSkillLevelBelongsToSkill(skillLevel, skill);

        if (userSkillDataAccess.existsByUserIdAndSkillId(userUuid, skillUuid)) {
            throw new IllegalArgumentException("Skill already bind to user");
        }

        Map<UUID, UUID> target = new LinkedHashMap<>();
        userSkillDataAccess.findByUserId(userUuid).forEach(item -> {
            UUID existingSkillId = item.getSkill().getId();
            UUID existingLevelId = item.getSkillLevel() == null ? null : item.getSkillLevel().getId();
            target.put(existingSkillId, existingLevelId);
        });
        target.put(skillUuid, skillLevelUuid);

        rebindUserSkills(userUuid, target);
    }

    @Transactional
    @Override
    @CacheEvict(value = "currentUserSkills", allEntries = true)
    public void rebindUserSkills(UUID userId, Map<UUID, UUID> skillLevelMapping) {
        if (userId == null) {
            throw new IllegalArgumentException("Key must not be null");
        }

        if (!userServiceFeignClient.existsUserById(userId)) {
            throw new IllegalArgumentException("User not found");
        }
        User user = new User();
        user.setId(userId);

        Map<UUID, UUID> targetMap = normalizeSkillLevelMapping(skillLevelMapping);
        validateSkillLevelMapping(targetMap);

        List<UserSkill> existingBindings = userSkillDataAccess.findByUserId(userId);
        Map<UUID, UserSkill> existingMap = new HashMap<>();
        for (UserSkill existing : existingBindings) {
            existingMap.put(existing.getSkill().getId(), existing);
        }

        for (Map.Entry<UUID, UserSkill> existingEntry : existingMap.entrySet()) {
            if (!targetMap.containsKey(existingEntry.getKey())) {
                userSkillDataAccess.deleteByUserIdAndSkillId(userId, existingEntry.getKey());
            }
        }

        for (Map.Entry<UUID, UUID> targetEntry : targetMap.entrySet()) {
            UUID targetSkillId = targetEntry.getKey();
            UUID targetLevelId = targetEntry.getValue();
            UserSkill existingBinding = existingMap.get(targetSkillId);

            if (existingBinding == null) {
                UserSkill newBinding = new UserSkill();
                newBinding.setUser(user);
                newBinding.setSkill(skillDataAccess.findById(targetSkillId)
                        .orElseThrow(() -> new IllegalArgumentException("Skill not found")));
                newBinding.setSkillLevel(skillLevelDataAccess.findById(targetLevelId)
                        .orElseThrow(() -> new IllegalArgumentException("Skill level not found")));
                userSkillDataAccess.save(newBinding);
                continue;
            }

            UUID existingLevelId = existingBinding.getSkillLevel() == null
                    ? null
                    : existingBinding.getSkillLevel().getId();
            if (!Objects.equals(existingLevelId, targetLevelId)) {
                SkillLevel level = skillLevelDataAccess.findById(targetLevelId)
                        .orElseThrow(() -> new IllegalArgumentException("Skill level not found"));
                existingBinding.setSkillLevel(level);
                userSkillDataAccess.save(existingBinding);
            }
        }
    }

    @Override
    @Transactional
    public void bindProjectSkill(String projectId, String skillId, String skillLevelId) {
        UUID projectUuid = mapUuid(projectId);
        UUID skillUuid = mapUuid(skillId);
        UUID skillLevelUuid = mapUuid(skillLevelId);
        if (projectUuid == null || skillUuid == null || skillLevelUuid == null) {
            throw new IllegalArgumentException("Key must not be null");
        }

        Project project = projectDataAccess.findById(projectUuid)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        Skill skill = skillDataAccess.findById(skillUuid)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found"));
        SkillLevel skillLevel = skillLevelDataAccess.findById(skillLevelUuid)
                .orElseThrow(() -> new IllegalArgumentException("Skill level not found"));
        validateSkillLevelBelongsToSkill(skillLevel, skill);

        if (projectSkillDataAccess.existsByProjectIdAndSkillId(projectUuid, skillUuid)) {
            throw new IllegalArgumentException("Skill already bind to project");
        }

        Map<UUID, UUID> target = new LinkedHashMap<>();
        projectSkillDataAccess.findByProjectId(projectUuid).forEach(item -> {
            UUID existingSkillId = item.getSkill().getId();
            UUID existingLevelId = item.getSkillLevel() == null ? null : item.getSkillLevel().getId();
            target.put(existingSkillId, existingLevelId);
        });
        target.put(skillUuid, skillLevelUuid);

        rebindProjectSkills(projectUuid, target);
    }

    @Transactional
    public void rebindProjectSkills(UUID projectId, Map<UUID, UUID> skillLevelMapping) {
        if (projectId == null) {
            throw new IllegalArgumentException("Key must not be null");
        }

        Project project = projectDataAccess.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        Map<UUID, UUID> targetMap = normalizeSkillLevelMapping(skillLevelMapping);
        validateSkillLevelMapping(targetMap);

        List<ProjectSkill> existingBindings = projectSkillDataAccess.findByProjectId(projectId);
        Map<UUID, ProjectSkill> existingMap = new HashMap<>();
        for (ProjectSkill existing : existingBindings) {
            existingMap.put(existing.getSkill().getId(), existing);
        }

        for (Map.Entry<UUID, ProjectSkill> existingEntry : existingMap.entrySet()) {
            if (!targetMap.containsKey(existingEntry.getKey())) {
                projectSkillDataAccess.deleteByProjectIdAndSkillId(projectId, existingEntry.getKey());
            }
        }

        for (Map.Entry<UUID, UUID> targetEntry : targetMap.entrySet()) {
            UUID targetSkillId = targetEntry.getKey();
            UUID targetLevelId = targetEntry.getValue();
            ProjectSkill existingBinding = existingMap.get(targetSkillId);

            if (existingBinding == null) {
                ProjectSkill newBinding = new ProjectSkill();
                newBinding.setProject(project);
                newBinding.setSkill(skillDataAccess.findById(targetSkillId)
                        .orElseThrow(() -> new IllegalArgumentException("Skill not found")));
                newBinding.setSkillLevel(skillLevelDataAccess.findById(targetLevelId)
                        .orElseThrow(() -> new IllegalArgumentException("Skill level not found")));
                projectSkillDataAccess.save(newBinding);
                continue;
            }

            UUID existingLevelId = existingBinding.getSkillLevel() == null
                    ? null
                    : existingBinding.getSkillLevel().getId();
            if (!Objects.equals(existingLevelId, targetLevelId)) {
                SkillLevel level = skillLevelDataAccess.findById(targetLevelId)
                        .orElseThrow(() -> new IllegalArgumentException("Skill level not found"));
                existingBinding.setSkillLevel(level);
                projectSkillDataAccess.save(existingBinding);
            }
        }
    }

    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "skills", key = "#skillVo.id"),
        @CacheEvict(value = "skills", key = "'all'")
    })
    public void deleteSkill(SkillVo skillVo) {
        Skill skill = skillMapper.toEntity(skillVo);
        if (skill.getId() == null) {
            throw new IllegalArgumentException("Key must not be null");
        }
        UUID skillId = skill.getId();
        skillDataAccess.findById(skillId).orElseThrow(() -> new IllegalArgumentException("Skill not found"));

        projectSkillDataAccess.deleteBySkillId(skillId);
        userSkillDataAccess.deleteBySkillId(skillId);
        skillLevelDataAccess.deleteBySkillId(skillId);
        skillDataAccess.deleteById(skillId);
    }

    @Override
    public SkillVo getSkillById(Long id) {
        Skill skill = skillDataAccess.findById(UUID.fromString(String.valueOf(id)))
                .orElseThrow(() -> new IllegalArgumentException("Skill not found"));
        return skillMapper.toVo(skill);
    }

    @Override
    public SkillLevelVo getSkillLevelById(Long id) {
        SkillLevel skillLevel = skillLevelDataAccess.findById(UUID.fromString(String.valueOf(id)))
                .orElseThrow(() -> new IllegalArgumentException("Skill level not found"));
        return mapSkillLevelVo(skillLevel);
    }
    
    @Override
    public PageResult<SkillVo> searchSkills(SkillSearchQuery query) {
        // 驗證排序欄位
        Set<String> validSortFields = Set.of("id", "name", "description", "createdBy", "updatedBy", "createdTime", "updatedTime");
        SortFieldValidator.validate(query.getSortBy(), query.getSortDir(), validSortFields);
        
        // 執行分頁查詢
        Page<Skill> page = skillDataAccess.searchSkills(query);
        
        // 轉換為 VO
        List<SkillVo> content = page.getContent().stream()
                .map(skillMapper::toVo)
                .collect(Collectors.toList());
        
        return PageResult.of(page, content);
    }
    
    @Override
    @Cacheable(value = "currentUserSkills", sync = true)
    public List<CurrentUserSkillVo> getCurrentUserSkills() {
        List<CurrentUserSkillVo> result = new ArrayList<>();
        Set<UUID> seenSkillIds = new HashSet<>();
        
        // 1. 取得 USER 直接綁定的技能
        List<UserSkill> userSkills = userSkillDataAccess.findByUserId(requireCurrentUserId());
        for (UserSkill us : userSkills) {
            Skill skill = us.getSkill();
            result.add(CurrentUserSkillVo.fromSkillVo(skillMapper.toVo(skill)));
            seenSkillIds.add(skill.getId());
        }
        
        // 2. 取得 PROJECT 專案的技能
        List<UserProject> userProjects = userProjectDataAccess.findByUserId(requireCurrentUserId());
        for (UserProject up : userProjects) {
            Project project = up.getProject();
            for (ProjectSkill ps : project.getProjectSkills()) {
                Skill skill = ps.getSkill();
                if (!seenSkillIds.contains(skill.getId())) {
                    result.add(CurrentUserSkillVo.fromSkillVoWithProject(
                        skillMapper.toVo(skill),
                        project.getId(),
                        project.getName()
                    ));
                    seenSkillIds.add(skill.getId());
                }
            }
        }
        
        return result;
    }
    
    @Override
    public PageResult<CurrentUserSkillVo> searchCurrentUserSkills(SkillSearchQuery query) {
        // 驗證排序欄位
        Set<String> validSortFields = Set.of("id", "name", "description", "createdBy", "updatedBy", "createdTime", "updatedTime");
        SortFieldValidator.validate(query.getSortBy(), query.getSortDir(), validSortFields);
        
        // 先取得所有當前使用者技能（已合併）
        List<CurrentUserSkillVo> allSkills = getCurrentUserSkills();
        
        // 套用搜尋條件
        List<CurrentUserSkillVo> filteredSkills = allSkills.stream()
                .filter(skill -> matchesQuery(skill, query))
                .collect(Collectors.toList());
        
        // 套用排序
        filteredSkills = applySorting(filteredSkills, query);
        
        // 套用分頁
        int start = query.getPage() * query.getSize();
        int end = Math.min(start + query.getSize(), filteredSkills.size());
        List<CurrentUserSkillVo> pageContent = (start < filteredSkills.size()) 
                ? filteredSkills.subList(start, end) 
                : new ArrayList<>();
        
        // 建立分頁結果
        return PageResult.<CurrentUserSkillVo>builder()
                .content(pageContent)
                .totalElements((long) filteredSkills.size())
                .totalPages((int) Math.ceil((double) filteredSkills.size() / query.getSize()))
                .currentPage(query.getPage())
                .pageSize(query.getSize())
                .hasNext(end < filteredSkills.size())
                .hasPrevious(query.getPage() > 0)
                .isFirst(query.getPage() == 0)
                .isLast(end >= filteredSkills.size())
                .build();
    }
    
    private boolean matchesQuery(CurrentUserSkillVo skill, SkillSearchQuery query) {
        if (query.getName() != null && !query.getName().trim().isEmpty()) {
            if (skill.getName() == null || !skill.getName().toLowerCase().contains(query.getName().toLowerCase())) {
                return false;
            }
        }
        
        if (query.getDescription() != null && !query.getDescription().trim().isEmpty()) {
            if (skill.getDescription() == null || !skill.getDescription().toLowerCase().contains(query.getDescription().toLowerCase())) {
                return false;
            }
        }
        
        if (query.getCreatedBy() != null && !query.getCreatedBy().trim().isEmpty()) {
            if (!query.getCreatedBy().equals(skill.getCreatedBy())) {
                return false;
            }
        }
        
        return true;
    }
    
    private List<CurrentUserSkillVo> applySorting(List<CurrentUserSkillVo> skills, SkillSearchQuery query) {
        boolean ascending = "asc".equalsIgnoreCase(query.getSortDir());
        
        return skills.stream()
                .sorted((s1, s2) -> {
                    int comparison = 0;
                    switch (query.getSortBy()) {
                        case "name":
                            comparison = compareNullable(s1.getName(), s2.getName());
                            break;
                        case "description":
                            comparison = compareNullable(s1.getDescription(), s2.getDescription());
                            break;
                        case "createdBy":
                            comparison = compareNullable(s1.getCreatedBy(), s2.getCreatedBy());
                            break;
                        case "updatedBy":
                            comparison = compareNullable(s1.getUpdatedBy(), s2.getUpdatedBy());
                            break;
                        case "createdTime":
                            comparison = compareNullable(s1.getCreatedTime(), s2.getCreatedTime());
                            break;
                        case "updatedTime":
                            comparison = compareNullable(s1.getUpdatedTime(), s2.getUpdatedTime());
                            break;
                        case "id":
                        default:
                            comparison = compareNullable(s1.getId(), s2.getId());
                            break;
                    }
                    return ascending ? comparison : -comparison;
                })
                .collect(Collectors.toList());
    }
    
    @Override
    public PageResult<SkillLevelVo> searchSkillLevels(SkillLevelSearchQuery query) {
        // 驗證排序欄位
        Set<String> validSortFields = Set.of("id", "levelValue", "title", "description", "createdBy", "updatedBy", "createdTime", "updatedTime");
        SortFieldValidator.validate(query.getSortBy(), query.getSortDir(), validSortFields);
        
        // 執行分頁查詢
        Page<SkillLevel> page = skillLevelDataAccess.searchSkillLevels(query);
        
        // 轉換為 VO
        List<SkillLevelVo> content = page.getContent().stream()
                .map(this::mapSkillLevelVo)
                .collect(Collectors.toList());
        
        return PageResult.of(page, content);
    }
    
    @SuppressWarnings("unchecked")
    private <T extends Comparable<T>> int compareNullable(T o1, T o2) {
        if (o1 == null && o2 == null) return 0;
        if (o1 == null) return -1;
        if (o2 == null) return 1;
        return o1.compareTo(o2);
    }

    private SkillLevelVo mapSkillLevelVo(SkillLevel skillLevel) {
        SkillLevelVo vo = new SkillLevelVo();
        vo.setId(skillLevel.getId() == null ? null : skillLevel.getId().toString());
        vo.setSkillId(skillLevel.getSkill() == null ? null : skillLevel.getSkill().getId().toString());
        vo.setLevelValue(skillLevel.getLevelValue());
        vo.setTitle(skillLevel.getTitle());
        vo.setDescription(skillLevel.getDescription());
        vo.setCreatedBy(skillLevel.getCreatedBy());
        vo.setUpdatedBy(skillLevel.getUpdatedBy());
        vo.setCreatedTime(skillLevel.getCreatedTime());
        vo.setUpdatedTime(skillLevel.getUpdatedTime());
        return vo;
    }

    private void validateLevelInput(SkillLevelVo skillLevelVo) {
        if (skillLevelVo.getLevelValue() == null || skillLevelVo.getLevelValue() < 1) {
            throw new IllegalArgumentException("Level value must be greater than 0");
        }
        if (skillLevelVo.getTitle() == null || skillLevelVo.getTitle().isBlank()) {
            throw new IllegalArgumentException("Title must not be null");
        }
    }

    private void validateSkillLevelBelongsToSkill(SkillLevel skillLevel, Skill skill) {
        if (!skillLevel.getSkill().getId().equals(skill.getId())) {
            throw new IllegalArgumentException("Skill level does not belong to skill");
        }
    }

    private UUID mapUuid(String id) {
        return id == null || id.isBlank() ? null : UUID.fromString(id);
    }

    private Map<UUID, UUID> normalizeSkillLevelMapping(Map<UUID, UUID> skillLevelMapping) {
        if (skillLevelMapping == null || skillLevelMapping.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UUID> normalized = new LinkedHashMap<>();
        for (Map.Entry<UUID, UUID> entry : skillLevelMapping.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("Key must not be null");
            }
            normalized.put(entry.getKey(), entry.getValue());
        }
        return normalized;
    }

    private void validateSkillLevelMapping(Map<UUID, UUID> targetMap) {
        for (Map.Entry<UUID, UUID> entry : targetMap.entrySet()) {
            Skill skill = skillDataAccess.findById(entry.getKey())
                    .orElseThrow(() -> new IllegalArgumentException("Skill not found"));
            SkillLevel skillLevel = skillLevelDataAccess.findById(entry.getValue())
                    .orElseThrow(() -> new IllegalArgumentException("Skill level not found"));
            validateSkillLevelBelongsToSkill(skillLevel, skill);
        }
    }
    
    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "skills", key = "'all'"),
        @CacheEvict(value = "currentUserSkills", allEntries = true)
    })
    public SkillVo addPersonalSkill(PersonalSkillRequest request) {
        // 驗證輸入
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Name must not be null");
        }
        
        // 檢查名稱是否已存在
        Skill existingSkill = new Skill();
        existingSkill.setName(request.getName());
        Example<Skill> example = Example.of(existingSkill);
        if (skillDataAccess.exists(example)) {
            throw new IllegalArgumentException("Name already exists");
        }
        
        // 建立技能
        Skill skill = new Skill();
        skill.setName(request.getName());
        skill.setDescription(request.getDescription());
        Skill savedSkill = skillDataAccess.save(skill);
        
        // 自動綁定當前使用者
        UserSkill userSkill = new UserSkill();
        User userRef = new User();
        userRef.setId(requireCurrentUserId());
        userSkill.setUser(userRef);
        userSkill.setSkill(savedSkill);

        SkillLevel bindingLevel;
        if (request.getSkillLevelId() != null && !request.getSkillLevelId().trim().isEmpty()) {
            UUID skillLevelId = UUID.fromString(request.getSkillLevelId());
            SkillLevel skillLevel = skillLevelDataAccess.findById(skillLevelId)
                    .orElseThrow(() -> new IllegalArgumentException("Skill level not found"));

            if (!skillLevel.getSkill().getId().equals(savedSkill.getId())) {
                throw new IllegalArgumentException("Skill level does not belong to skill");
            }
            bindingLevel = skillLevel;
        } else if (hasManualSkillLevelInput(request)) {
            bindingLevel = createManualSkillLevel(savedSkill, request);
        } else {
            List<SkillLevel> levels = skillLevelDataAccess.findBySkillIdOrderByLevelValueAsc(savedSkill.getId());
            if (levels.isEmpty()) {
                throw new IllegalArgumentException("Skill level data is required");
            }
            bindingLevel = levels.get(0);
        }

        userSkill.setSkillLevel(bindingLevel);
        
        userSkillDataAccess.save(userSkill);
        
        return skillMapper.toVo(savedSkill);
    }
    
    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "skills", key = "#skillId"),
        @CacheEvict(value = "skills", key = "'all'"),
        @CacheEvict(value = "currentUserSkills", allEntries = true)
    })
    public void updatePersonalSkill(UUID skillId, PersonalSkillRequest request) {
        // 驗證輸入
        if (skillId == null) {
            throw new IllegalArgumentException("Skill ID must not be null");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Name must not be null");
        }
        
        // 查找技能
        Skill skill = skillDataAccess.findById(skillId)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found"));
        
        // 驗證是否為擁有者（檢查是否有綁定關係）
        if (!userSkillDataAccess.existsByUserIdAndSkillId(requireCurrentUserId(), skillId)) {
            throw new IllegalArgumentException("You are not the owner of this skill");
        }

        if (!canEditContent(skill.getCreatedBy(), requireCurrentUserId())) {
            throw new IllegalArgumentException("Skill assigned by admin is read-only");
        }
        
        // 更新技能資訊
        skill.setName(request.getName());
        skill.setDescription(request.getDescription());
        skillDataAccess.save(skill);
        
        // 如果提供了新的技能等級，更新綁定的等級
        if (request.getSkillLevelId() != null && !request.getSkillLevelId().trim().isEmpty()) {
            UUID skillLevelId = UUID.fromString(request.getSkillLevelId());
            SkillLevel skillLevel = skillLevelDataAccess.findById(skillLevelId)
                    .orElseThrow(() -> new IllegalArgumentException("Skill level not found"));
            
            // 驗證技能等級屬於該技能
            if (!skillLevel.getSkill().getId().equals(skillId)) {
                throw new IllegalArgumentException("Skill level does not belong to skill");
            }
            
            // 更新 UserSkill 的等級
            List<UserSkill> userSkills = userSkillDataAccess.findByUserIdAndSkillId(requireCurrentUserId(), skillId);
            if (!userSkills.isEmpty()) {
                UserSkill userSkill = userSkills.get(0);
                userSkill.setSkillLevel(skillLevel);
                userSkillDataAccess.save(userSkill);
            }
        }
    }

    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "skills", key = "#skillId"),
        @CacheEvict(value = "skills", key = "'all'"),
        @CacheEvict(value = "currentUserSkills", allEntries = true)
    })
    public void updatePersonalSkillLevel(UUID skillId, UUID skillLevelId) {
        if (skillId == null || skillLevelId == null) {
            throw new IllegalArgumentException("Key must not be null");
        }

        Skill skill = skillDataAccess.findById(skillId)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found"));
        if (!userSkillDataAccess.existsByUserIdAndSkillId(requireCurrentUserId(), skillId)) {
            throw new IllegalArgumentException("Skill is not bind to current user");
        }

        SkillLevel skillLevel = skillLevelDataAccess.findById(skillLevelId)
                .orElseThrow(() -> new IllegalArgumentException("Skill level not found"));
        validateSkillLevelBelongsToSkill(skillLevel, skill);

        List<UserSkill> userSkills = userSkillDataAccess.findByUserIdAndSkillId(requireCurrentUserId(), skillId);
        if (userSkills.isEmpty()) {
            throw new IllegalArgumentException("Skill is not bind to current user");
        }

        UserSkill userSkill = userSkills.get(0);
        userSkill.setSkillLevel(skillLevel);
        userSkillDataAccess.save(userSkill);
    }
    
    @Transactional
    @Override
    @Caching(evict = {
        @CacheEvict(value = "skills", key = "#skillId"),
        @CacheEvict(value = "skills", key = "'all'"),
        @CacheEvict(value = "currentUserSkills", allEntries = true)
    })
    public void deletePersonalSkill(UUID skillId) {
        // 驗證輸入
        if (skillId == null) {
            throw new IllegalArgumentException("Skill ID must not be null");
        }
        
        // 查找技能
        Skill skill = skillDataAccess.findById(skillId)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found"));
        
        // 驗證是否為擁有者
        if (!userSkillDataAccess.existsByUserIdAndSkillId(requireCurrentUserId(), skillId)) {
            throw new IllegalArgumentException("You are not the owner of this skill");
        }

        // 刪除當前使用者與技能的綁定
        userSkillDataAccess.deleteByUserIdAndSkillId(requireCurrentUserId(), skillId);
    }

    private boolean canEditContent(String createdBy, UUID currentUserId) {
        if (createdBy == null || createdBy.isBlank()) {
            return true;
        }
        return createdBy.equals(currentUserId.toString());
    }

    private UUID requireCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalStateException("Current user not found - no authentication");
        }
        String email = auth.getName();
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("Current user not found - no email in authentication");
        }
        UserVo userVo = userServiceFeignClient.getUserByEmail(email);
        if (userVo == null || userVo.getId() == null) {
            throw new IllegalStateException("Current user not found - user lookup failed");
        }
        return UUID.fromString(userVo.getId());
    }

    private boolean hasManualSkillLevelInput(PersonalSkillRequest request) {
        return request.getSkillLevelValue() != null
                || (request.getSkillLevelTitle() != null && !request.getSkillLevelTitle().isBlank())
                || (request.getSkillLevelDescription() != null && !request.getSkillLevelDescription().isBlank());
    }

    private SkillLevel createManualSkillLevel(Skill skill, PersonalSkillRequest request) {
        if (request.getSkillLevelValue() == null || request.getSkillLevelValue() < 1) {
            throw new IllegalArgumentException("Skill level value must be greater than 0");
        }
        if (request.getSkillLevelTitle() == null || request.getSkillLevelTitle().isBlank()) {
            throw new IllegalArgumentException("Skill level title must not be null");
        }

        SkillLevel skillLevel = new SkillLevel();
        skillLevel.setSkill(skill);
        skillLevel.setLevelValue(request.getSkillLevelValue());
        skillLevel.setTitle(request.getSkillLevelTitle());
        skillLevel.setDescription(request.getSkillLevelDescription());
        return skillLevelDataAccess.save(skillLevel);
    }
}
