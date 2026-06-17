# Redis 快取策略文件

## 一、現有快取基礎設施

### Redis 連線
- **連線方式**: Lettuce ConnectionFactory
- **Host 解析邏輯**: `REDIS_HOST` 環境變數 → `APP_IN_DOCKER` 判斷（docker 內為 `redis`，本機為 `localhost`）
- **預設 port**: `6379`

### 序列化
- **Key 序列化**: `StringRedisSerializer`
- **Value 序列化**: `GenericJackson2JsonRedisSerializer`（支援多態類型）

### 全域預設
- **預設 TTL**: 1 小時（可透過 `REDIS_CACHE_TTL_HOURS` 環境變數調整）
- **快取管理器**: `RedisCacheManager`，支援 `@Cacheable`、`@CachePut`、`@CacheEvict` 註解

---

## 二、快取策略總覽

| 層級 | 資料類型 | TTL 範圍 | 策略說明 |
|------|---------|---------|---------|
| **L1 - 參考資料** | Skill, Role, Function, SkillLevel | 6 ~ 24 小時 | 極少變更，長 TTL，寫時精確 key 清除 |
| **L2 - 業務資料** | Company, JobPosting, Project | 30 分鐘 ~ 6 小時 | 中等變更頻率，精確 key + 批量全清除 |
| **L3 - 使用者資料** | UserSkills, UserProjects, UserJobLinks | 10 分鐘 | 個人化資料，短 TTL，關聯 key 精確清除 |
| **L4 - 運算結果** | 平均值聚合, 成員技能關聯 | 30 分鐘 | 計算密集型，節省 CPU |

---

## 三、完整快取配置（RedisConfig）

| 快取名稱空間 | TTL | 資料說明 | 清理時機 |
|-------------|-----|---------|---------|
| `users` | 2 小時 | 使用者基本資訊 + Email 映射 | 修改/刪除使用者時清除 |
| `alertCheckLimit` | 1 小時 | 告警檢查閾值 | 更新/刪除閾值時清除 |
| `aquarkData` | 1 小時 | IoT 水文資料 | 更新資料時清除 |
| `skills` | 24 小時 | 技能定義列表與單項 | 新增 `@CachePut(id)` + evict `'all'`；修改/刪除 evict(id + 'all') |
| `skillLevels` | 24 小時 | 特定技能的等級列表 | 新增/修改/刪除時 evict(skillId + 'all')；刪除使用 CacheManager 取得 skillId |
| `roles` | 6 小時 | 角色列表、單項角色、角色名稱 | 新增 `@CachePut(id + byname)`；修改 `@CachePut(id + byname)`；刪除 evict(id)；functionBind/Unbind 精確 evict(`'byfunction:'+#id` + 各 `functions:roleId`) |
| `roleFunctions` | 6 小時 | 角色對應的功能權限 | 角色綁定/解除功能時 evict(`#roleId`) |
| `functions` | 24 小時 | 功能樹列表、單項功能、功能名稱 | 新增 `@CachePut(id+byname)` + evict(bynameparent)；修改/刪除 evict(id+byname+bynameparent) |
| `companies` | 6 小時 | 公司列表與單項公司 | 新增 `@CacheEvict(key="'all'")`；修改 `@CachePut(id)+@CacheEvict(key="'all'")`；刪除 evict(id+'all') |
| `jobPostings` | 1 小時 | 職缺列表與單項職缺 | 新增 `@CachePut(id)` + evict(bycompany+'all')；修改同；刪除使用 CacheManager evict(id+bycompany+'all'+search:*) |
| `projectSkills` | 30 分鐘 | 專案關聯技能 | 綁定/解除/rebind 技能時 evict(`#projectId`)；新增/更新/刪除專案時不影響此快取 |
| `projectMemberSkills` | 30 分鐘 | 專案成員技能（N+1 高風險） | 成員技能變更時清除全部（批次無法反推 key） |
| `projects` | 10 分鐘 | 當前使用者專案清單 | rebindUserProjects evict(`'current:'+#userId`)；其餘精確清除 `'all'` + `'byuser:'+#userId` |
| `currentUserSkills` | 10 分鐘 | 當前使用者技能（多表合併） | 使用 `evictCurrentUserSkills(userId)` 精確清除特定使用者 |
| `userJobLinks` | 10 分鐘 | 使用者職缺收藏清單 | 新增 `@CachePut(id)` + evict(byuser+byjob+currentuser)；刪除使用 CacheManager |
| `userRoles` | 10 分鐘 | 使用者角色清單 | 角色綁定/解除時 evict(`'byrole:'+#roleId`) 精確清除 |
| `aquarkDataAvg` | 30 分鐘 | 平均數據（運算密集型） | TTL 到期自動失效 |

---

## 四、各 Service 快取實作細節

### 4.1 UserService（快取名稱: `users`）

#### 現有問題修復
- **`getUserById`**: `@Cacheable(key = "#result.id")` → 改為 `#id`
- **`saveUserWithRole`**: 新增 `@CacheEvict(value = "users", allEntries = true)`

#### 快取方法

```java
// 查詢 - 快取
@Cacheable(value = "users", key = "#email", unless = "#result == null")
UserVo getOnlyUserByEmail(String email);

@Cacheable(value = "users", key = "#id", unless = "#result == null")
UserVo getUserById(String id);

// 寫入 - 更新快取
@Caching(put = {
    @CachePut(value = "users", key = "#result.id", unless = "#result == null"),
    @CachePut(value = "users", key = "#result.email", unless = "#result == null")
})
UserVo createUser(UserVo userVo);

@Caching(put = {
    @CachePut(value = "users", key = "#result.id", unless = "#result == null"),
    @CachePut(value = "users", key = "#result.email", unless = "#result == null")
})
UserVo saveUser(UserVo userVo);

// 寫入 - 清除快取
@CacheEvict(value = "users", allEntries = true)
void saveUserWithRole(UserVo userVo);  // 混合新增/修改，保留全量清除

@CacheEvict(value = "users", key = "#userId")
void rebindUserRoles(UUID userId, List<String> roleIds);  // 精確 key

@CacheEvict(value = "userProjects", key = "'current:' + #userId")
void rebindUserProjects(UUID userId, List<UUID> projectIds);  // 精確 key
```

---

### 4.2 SkillService（快取名稱: `skills`, `skillLevels`, `currentUserSkills`）

```java
@Cacheable(value = "skills", key = "'all'", sync = true)
List<SkillVo> getSkill();

@Cacheable(value = "skillLevels", key = "#skillId", sync = true)
List<SkillLevelVo> getSkillLevels(String skillId);

@Cacheable(value = "currentUserSkills", key = "'byuser:' + #currentUserId", sync = true)
List<CurrentUserSkillVo> getCurrentUserSkills();

// 寫入操作 - @CachePut + 精確 key evict（不再全量清除）
@Caching(put = {
    @CachePut(value = "skills", key = "#result.id")
}, evict = {
    @CacheEvict(value = "skills", key = "'all'")
})
SkillVo addSkill(SkillVo skillVo);  // @CachePut + evict 'all' 列表

@Caching(put = {
    @CachePut(value = "skills", key = "#skillVo.id")
}, evict = {
    @CacheEvict(value = "skills", key = "'all'")
})
void updateSkill(SkillVo skillVo);  // @CachePut + evict 'all' 列表

@Caching(evict = {
    @CacheEvict(value = "skills", key = "#skillVo.id"),
    @CacheEvict(value = "skills", key = "'all'"),
    @CacheEvict(value = "skillLevels", key = "#skillVo.id")
})
void deleteSkill(SkillVo skillVo);  // evict id + 'all' + skillLevels

@Caching(evict = {
    @CacheEvict(value = "skills", key = "'all'"),
    @CacheEvict(value = "currentUserSkills", key = "'byuser:' + #currentUserId")
})
SkillVo addPersonalSkill(PersonalSkillRequest request);  // evict 'all' + 特定使用者快取

@Caching(put = {
    @CachePut(value = "skills", key = "#skillId")
}, evict = {
    @CacheEvict(value = "skills", key = "'all'"),
    @CacheEvict(value = "currentUserSkills", key = "'byuser:' + #currentUserId")
})
void updatePersonalSkill(UUID skillId, PersonalSkillRequest request);

// 完全不移除 skills 快取（只改個人綁定，不影響技能資料）
void updatePersonalSkillLevel(UUID skillId, UUID skillLevelId);
// 內部呼叫 evictCurrentUserSkills(currentUserId) 精確清除

// 完全不移除 skills 快取（只改個人綁定）
void deletePersonalSkill(UUID skillId);
// 內部呼叫 evictCurrentUserSkills(currentUserId) 精確清除

// 技能等級操作 - 精確清除 skillLevels
@CacheEvict(value = "skillLevels", key = "#skillLevelVo.skillId")
SkillLevelVo addSkillLevel(SkillLevelVo skillLevelVo);  // 僅 evict 該技能的等級列表
// 內部呼叫 evictSkillsLevelSearchCache() 清除 skills::*levelsearch:* 前綴鍵

@CacheEvict(value = "skillLevels", key = "#skillLevelVo.skillId")
void updateSkillLevel(SkillLevelVo skillLevelVo);  // 僅 evict 該技能的等級列表
// 內部呼叫 evictSkillsLevelSearchCache() 清除 skills::*levelsearch:* 前綴鍵

// 需先查詢 entity 取得 skillId → 使用 CacheManager
void deleteSkillLevel(String skillLevelId);  // CacheManager evict(skillId) + evictSkillsLevelSearchCache()

// rebind - 精確清除特定使用者（不再 allEntries）
void rebindUserSkills(UUID userId, Map<UUID, UUID> skillLevelMapping);
// 內部呼叫 evictCurrentUserSkills(userId) 精確清除
```

---

### 4.3 RoleService（快取名稱: `roles`, `roleFunctions`, `userRoles`）

```java
@Cacheable(value = "roles", key = "'all'", sync = true)
List<RoleOutVo> getRole();

@Cacheable(value = "roles", key = "#roleId", sync = true)
RoleOutVo getRoleById(String roleId);

@Cacheable(value = "roleFunctions", key = "#roleId", sync = true)
List<FunctionVo> getFunctionByRole(String roleId);

@Cacheable(value = "roles", key = "'byname:' + #name", sync = true)
RoleOutVo getRoleByName(String name);

@Cacheable(value = "roles", key = "'byuser:' + #userId", sync = true)
List<RoleOutVo> getRoleByUser(String userId);

// 寫入操作 - @CachePut + 精確 key evict
@Caching(put = {
    @CachePut(value = "roles", key = "#result.id"),
    @CachePut(value = "roles", key = "'byname:' + #result.name")
})
RoleOutVo addRole(RoleOutVo roleOutVo);

@Caching(put = {
    @CachePut(value = "roles", key = "#roleOutVo.id"),
    @CachePut(value = "roles", key = "'byname:' + #roleOutVo.name")
})
RoleOutVo updateRole(RoleOutVo roleOutVo);

@Caching(evict = {
    @CacheEvict(value = "roles", key = "#roleOutVo.id"),
    @CacheEvict(value = "roleFunctions", key = "#roleOutVo.id")
})
void deleteRole(RoleOutVo roleOutVo);

@CacheEvict(value = "roleFunctions", key = "#roleId")
void roleBindFunction(String roleId, List<String> functionIds);

@CacheEvict(value = "roleFunctions", key = "#roleId")
void roleUnbindFunction(String roleId, List<String> functionIds);

// functionBind/Unbind - 精確清除（不再全量清除）
functionBindRole(String functionId, List<String> roleIds);
// 程式化清除: evict(roles::byfunction:{functionId}) + 逐個 evict(roleFunctions::{roleId})

functionUnbindRole(String functionId, List<String> roleIds);
// 同上，精確清除

// 使用者綁定操作 - 精確清除（不再全量清除）
userBindRole(String userId, List<String> roleIds);
// 程式化清除: evict(userRoles::byrole:{roleId}) 逐個 + evict(roles::byuser:{userId})

userUnbindRole(String userId, List<String> roleIds);
// 同上精確清除

userUnbindAllRole(String userId);
// 先查使用者角色，再逐個 evict(userRoles::byrole:{roleId})

// 批量使用者操作 - 精確清除（不再全量清除）
roleBindUser(String roleId, List<String> userIds);
// 程式化清除: evict(roles::byuser:{userId}) 逐個 + evict(userRoles::byrole:{roleId})

roleUnbindUser(String roleId, List<String> userIds);
// 同上精確清除
```

---

### 4.4 FunctionService（快取名稱: `functions`）

```java
@Cacheable(value = "functions", key = "'all'", sync = true)
List<FunctionVo> getFunction();

@Cacheable(value = "functions", key = "#id", sync = true)
FunctionVo getFunctionById(String id);

@Cacheable(value = "functions", key = "'byname:' + #name", sync = true)
FunctionVo getFunctionByName(String name);

@Cacheable(value = "functions", key = "'bynameparent:' + #name + ':' + #parent", sync = true)
FunctionVo getFunctionByNameAndParent(String name, String parent);

// 寫入操作 - 精確清除
@Caching(put = {
    @CachePut(value = "functions", key = "#result.id"),
    @CachePut(value = "functions", key = "'byname:' + #result.name")
}, evict = {
    @CacheEvict(value = "functions", key = "'bynameparent:' + #result.name + ':' + (#result.parent != null ? #result.parent : '')")
})
FunctionVo addFunction(FunctionVo functionVo);  // @CachePut + evict bynameparent

@Caching(evict = {
    @CacheEvict(value = "functions", key = "#functionVo.id"),
    @CacheEvict(value = "functions", key = "'byname:' + #functionVo.name"),
    @CacheEvict(value = "functions", key = "'bynameparent:' + #functionVo.name + ':' + (#functionVo.parent != null ? #functionVo.parent : '')")
})
void updateFunction(FunctionVo functionVo);  // evict 三項精確 key

@CacheEvict(value = "functions", key = "#functionVo.id")
void deleteFunction(FunctionVo functionVo);  // evict 單項 id

// 批次操作 → 無法反推各 function key → 保留全量清除
@CacheEvict(value = "functions", allEntries = true)
void deleteFunction(List<FunctionVo> function);

@CacheEvict(value = "functions", allEntries = true)
void saveFunction(List<FunctionVo> function);

@CacheEvict(value = "functions", allEntries = true)
List<FunctionVo> saveFunctionNewChild(List<FunctionVo> function);
```

---

### 4.5 ProjectService（快取名稱: `projectSkills`, `projectMemberSkills`, `projects`）

```java
@Cacheable(value = "projects", key = "'all'", sync = true)
List<ProjectVo> getProject();

@Cacheable(value = "projects", key = "'byuser:' + #currentUserId", sync = true)
List<ProjectVo> getCurrentUserProjects();

@Cacheable(value = "projectSkills", key = "#projectId", sync = true)
List<ProjectSkillVo> getProjectSkills(UUID projectId);

@Cacheable(value = "projectMemberSkills", key = "#projectId", sync = true)  // 無快取 evict
List<ProjectMemberSkillVo> getProjectMemberSkills(UUID projectId);

// projects 精確清除 'all' + 受影響使用者；projectSkills 不影響（無技能綁定）
@Caching(evict = {
    @CacheEvict(value = "projects", key = "'all'")
})
ProjectVo addProject(ProjectVo projectVo);
// 內部呼叫 evictUserProjectsCache(各綁定使用者 userId)

@Caching(evict = {
    @CacheEvict(value = "projects", key = "'all'")
})
void updateProject(ProjectVo projectVo);
// 內部呼叫 evictUserProjectsCache(各綁定使用者 userId)

@Caching(evict = {
    @CacheEvict(value = "projects", key = "#projectVo.id"),
    @CacheEvict(value = "projects", key = "'all'"),
    @CacheEvict(value = "projectSkills", key = "#projectVo.id")
})
void deleteProject(ProjectVo projectVo);

// 個人專案操作 - 精確清除 'all' + 當前使用者
@Caching(evict = {
    @CacheEvict(value = "projects", key = "'all'")
})
ProjectVo addPersonalProject(PersonalProjectRequest request);
// 內部呼叫 evictUserProjectsCache(currentUserId)

void updatePersonalProject(UUID projectId, PersonalProjectRequest request);
// 內部呼叫 evictUserProjectsCache(currentUserId)

void deletePersonalProject(UUID projectId);
// 內部呼叫 evictUserProjectsCache(currentUserId) + evict(projects::all)

// projectSkills 精確 evict（已知 projectId）
@CacheEvict(value = "projectSkills", key = "#projectId")
void bindPersonalProjectSkill(UUID projectId, UUID skillId, UUID skillLevelId);

@CacheEvict(value = "projectSkills", key = "#projectId")
void updatePersonalProjectSkillLevel(UUID projectId, UUID skillId, UUID skillLevelId);

@CacheEvict(value = "projectSkills", key = "#projectId")
void unbindPersonalProjectSkill(UUID projectId, UUID skillId);

@CacheEvict(value = "projectSkills", key = "#projectId")
void rebindProjectSkills(UUID projectId, Map<UUID, UUID> skillLevelMapping);

@CacheEvict(value = "projectSkills", key = "#projectId")
void rebindPersonalProjectSkills(UUID projectId, Map<UUID, UUID> skillLevelMapping);

// 僅清除 projectSkills（未改變專案成員資格，projects 快取不受影響）
@CacheEvict(value = "projectSkills", key = "#projectId")
void rebindProjectMemberSkills(UUID projectId, Map<UUID, Map<UUID, UUID>> memberSkillsMap);
```

---

### 4.6 CompanyService（快取名稱: `companies`）

```java
@Cacheable(value = "companies", key = "'all'", unless = "#result.isEmpty()")
List<CompanyVo> getAllCompanies();

@Cacheable(value = "companies", key = "#id", unless = "#result == null")
CompanyVo getCompanyById(String id);

// 寫入操作 - 精確清除（不再全量清除）
@CacheEvict(value = "companies", key = "'all'")
CompanyVo createCompany(CreateCompanyRequest request);  // 僅清除 'all' 列表

@Caching(put = {
    @CachePut(value = "companies", key = "#request.id")
}, evict = {
    @CacheEvict(value = "companies", key = "'all'")
})
CompanyVo updateCompany(UpdateCompanyRequest request);  // @CachePut(id) + evict('all')

@Caching(evict = {
    @CacheEvict(value = "companies", key = "#id"),
    @CacheEvict(value = "companies", key = "'all'")
})
void deleteCompany(String id);  // evict(id + 'all')
```

---

### 4.7 JobPostingService（快取名稱: `jobPostings`）

```java
@Cacheable(value = "jobPostings", key = "'all'", sync = true)
List<JobPostingVo> getAllJobPostings();

@Cacheable(value = "jobPostings", key = "#id", sync = true)
JobPostingVo getJobPostingById(String id);

@Cacheable(value = "jobPostings", key = "'bycompany:' + #companyId", sync = true)
List<JobPostingVo> getJobPostingsByCompanyId(String companyId);

@Cacheable(value = "jobPostings", key = "'search:' + #query.toString()", sync = true)
PageResult<JobPostingVo> searchJobPostings(JobPostingSearchQuery query);

// 寫入操作 - @CachePut + 精確 evict（補上 'all'）
@Caching(put = {
    @CachePut(value = "jobPostings", key = "#result.id")
}, evict = {
    @CacheEvict(value = "jobPostings", key = "'bycompany:' + #request.companyId"),
    @CacheEvict(value = "jobPostings", key = "'all'")
})
JobPostingVo createJobPosting(CreateJobPostingRequest request);  // @CachePut(id) + evict(bycompany + 'all')

@Caching(put = {
    @CachePut(value = "jobPostings", key = "#jobPostingVo.id")
}, evict = {
    @CacheEvict(value = "jobPostings", key = "'bycompany:' + #jobPostingVo.companyId"),
    @CacheEvict(value = "jobPostings", key = "'all'")
})
JobPostingVo updateJobPosting(JobPostingVo jobPostingVo);  // @CachePut(id) + evict(bycompany + 'all')

// 需先查詢 entity 取得 companyId → 使用 CacheManager
void deleteJobPosting(String id);
// CacheManager evict(id + bycompany + 'all') + evictJobPostingsSearchCache() 清除 'search:*'

@Caching(evict = {
    @CacheEvict(value = "jobPostings", key = "'bycompany:' + #companyId"),
    @CacheEvict(value = "jobPostings", key = "'all'"),
    @CacheEvict(value = "companies", key = "'all'")
})
List<JobPostingVo> scrapeAndAnalyzeJobs(String companyId);  // evict bycompany + 'all'
// 程式化清除 evict(companies::#companyId) 精確清除單一公司快取
```

---

### 4.8 AquarkDataService（快取名稱: `aquarkData`, `aquarkDataAvg`）

#### 現有問題修復
- `@Cacheable(value = "aquarkData", key = "#result.station_id + '_' + #result.trans_time.toString()")` → 改為 `#aquarkDataRaw.station_id + '_' + #aquarkDataRaw.trans_time`

```java
// 快取
@Cacheable(value = "aquarkData", key = "#aquarkDataRaw.station_id + '_' + #aquarkDataRaw.trans_time", unless = "#result == null")
AquarkDataVo getAquarkData(AquarkDataRaw aquarkDataRaw);

@Cacheable(value = "aquarkDataAvg", key = "#startTime.toString() + '_' + #endTime.toString()", unless = "#result == null")
Double getAverageAquark(Date startTime, Date endTime);

// 寫入 - 更新快取
@CachePut(value = "aquarkData", key = "#aquarkDataRaw.station_id + '_' + #aquarkDataRaw.trans_time")
@CacheEvict(value = "aquarkDataAvg", allEntries = true)
AquarkDataVo updateAquarkData(AquarkDataRaw aquarkDataRaw);
```

---

### 4.9 UserJobLinkService（快取名稱: `userJobLinks`）

```java
@Cacheable(value = "userJobLinks", sync = true)
List<UserJobLinkVo> getAllUserJobLinks();

@Cacheable(value = "userJobLinks", key = "#id", sync = true)
UserJobLinkVo getUserJobLinkById(String id);

@Cacheable(value = "userJobLinks", key = "'byuser:' + #userId", sync = true)
List<UserJobLinkVo> getUserJobLinksByUserId(String userId);

@Cacheable(value = "userJobLinks", key = "'byjob:' + #jobPostingId", sync = true)
List<UserJobLinkVo> getUserJobLinksByJobPostingId(String jobPostingId);

@Cacheable(value = "userJobLinks", key = "'currentuser:' + #currentUserId", sync = true)
List<UserJobLinkVo> getCurrentUserJobLinks(String currentUserId);

// 寫入操作 - @CachePut + 精確 evict
@Caching(put = {
    @CachePut(value = "userJobLinks", key = "#result.id")
}, evict = {
    @CacheEvict(value = "userJobLinks", key = "'byuser:' + #userJobLinkVo.userId"),
    @CacheEvict(value = "userJobLinks", key = "'byjob:' + #userJobLinkVo.jobPostingId"),
    @CacheEvict(value = "userJobLinks", key = "'currentuser:' + #userJobLinkVo.userId")
})
UserJobLinkVo createUserJobLink(UserJobLinkVo userJobLinkVo);  // @CachePut(id) + evict 3 項關聯 key

// 需先查詢 entity 取得 userId + jobPostingId → 使用 CacheManager
void deleteUserJobLink(String id);  // CacheManager evict(id + byuser + byjob + currentuser)

@Caching(put = {
    @CachePut(value = "userJobLinks", key = "#result.id")
}, evict = {
    @CacheEvict(value = "userJobLinks", key = "'byuser:' + #currentUserId"),
    @CacheEvict(value = "userJobLinks", key = "'byjob:' + #jobPostingId"),
    @CacheEvict(value = "userJobLinks", key = "'currentuser:' + #currentUserId")
})
UserJobLinkVo addJobToCurrentUser(String currentUserId, String jobPostingId);  // @CachePut + 精確 evict

@Caching(evict = {
    @CacheEvict(value = "userJobLinks", key = "'byuser:' + #currentUserId"),
    @CacheEvict(value = "userJobLinks", key = "'byjob:' + #jobPostingId"),
    @CacheEvict(value = "userJobLinks", key = "'currentuser:' + #currentUserId")
})
void removeJobFromCurrentUser(String currentUserId, String jobPostingId);  // 精確 evict
```

---

### 4.10 AlertCheckLimitService（快取名稱: `alertCheckLimit`）— 已完善，無需修改

```java
// 已實作（完整範例）
@Cacheable(value = "alertCheckLimit", key = "#apiId + '_' + #dataKey", unless = "#result == null")
AlertCheckLimitVo getLimit(String apiId, String dataKey);

@CachePut(value = "alertCheckLimit", key = "#apiId + '_' + #dataKey")
AlertCheckLimitVo insertLimit(String apiId, String dataKey, double value);

@CachePut(value = "alertCheckLimit", key = "#entity.apiId + '_' + #entity.dataKey")
AlertCheckLimit updateEntity(AlertCheckLimit entity);

@CacheEvict(value = "alertCheckLimit", key = "#entity.apiId + '_' + #entity.dataKey")
void deleteLimitEntity(AlertCheckLimit entity);
```

---

## 五、不建議快取的服務與原因

| 服務 | 說明 |
|------|------|
| **GeminiService** | 呼叫 Google Gemini 外部 API，每次爬取 HTML 不同，結果不適合快取 |
| **AlarmService** | 即時告警處理，需要即時一致性 |
| **AlarmKafkaPublisher** | Kafka 訊息發布，非資料查詢 |
| **KafkaConsumerService** | Kafka 訊息消費 + WebSocket 廣播，非資料查詢 |
| **CheckApiService** | IoT 資料檢查，即時性要求高 |
| **initAndCheckService** | 啟動初始化邏輯，僅執行一次 |
| **IProjectStore** | 儲存層介面，無具體實作 |

---

## 六、快取 Key 設計原則

| 資料類型 | Key 模式 | 範例 |
|---------|---------|------|
| 單項查詢（ID） | `#id` | `company.getId()` → `"a1b2c3d4-..."` |
| 單項查詢（名稱） | `#name` | `"admin"` |
| 複合查詢 | `#field1 + '_' + #field2` | `"station1_2024-01-01"` |
| 全量列表 | `'all'` | `"all"` |
| 關聯查詢 | `'prefix_' + #id` | `"company_a1b2c3d4-..."` |

---

## 七、快取失效策略

| 操作類型 | 失效策略 | 適用場景 |
|---------|---------|---------|
| **CUD 精確 key** | `@CachePut(key = "#result.id")` | create/update 後同時寫入快取（User, Function, Role, Skill, JobPosting, UserJobLink） |
| **CUD 單項 evict** | `@CacheEvict(key = "#entity.id")` | 已知 ID 的刪除操作（AlertCheckLimit, Skill, Function, Role） |
| **CUD 關聯 evict** | `@CacheEvict(key = "'prefix_' + #关联ID")` | 需同時清除關聯查詢結果（bycompany, byuser, byjob, byname, bynameparent, functions:roleId） |
| **CUD 複雜查詢** | `CacheManager.evict(key)` | 需先查 entity 才能取得關聯 ID 的情況（deleteJobPosting, deleteUserJobLink, deleteSkillLevel） |
| **CUD 全量清除** | `@CacheEvict(allEntries = true)` | 批次操作或無法反推 key 的情況（saveFunction, addProject, roleBindUser, userRoles） |
| **級聯清除** | `@Caching(evict = {...})` | 功能/角色變更時同時清除多個快取 |
| **TTL 自動過期** | `entryTtl(Duration.ofHours(n))` | 所有快取的最終保障 |

### 快取穿透防護（已實作）

採用雙層防護機制：

#### 第一層：空值快取（Null Value Caching）
- 自訂 `CachePenetrationProtectionCache` 包裝 `RedisCache`
- 當 DB 查詢結果為 null 時，存入 `NullValue` 佔位物件（短 TTL，預設 5 分鐘）
- `get()` 時自動判讀 `NullValue` 並轉換為 null 回傳
- 所有 `@Cacheable` 已移除 `unless = "#result == null"` 條件

#### 第二層：布隆過濾器（Bloom Filter）— 僅寫入路徑
- 基於 **Redisson RBloomFilter** 實作（`IBloomFilterService`）
- 每個 cache namespace 獨立過濾器，Redis key 格式：`bloom:{cacheName}`
- 預期資料量：10,000，誤判率：1%
- 啟動時透過 `BloomFilterInitializer` 從 DB 預先填充
- 資料新增/更新（`put()`）時自動加入過濾器
- **不參與讀取路徑**：因 SimpleKey.EMPTY 與名稱型 key（如 `'byname:admin'`）非 UUID 格式，BF 無法涵蓋，讀取防護依賴空值快取 + 分散式鎖
- 優點：分散式儲存於 Redis，多實例共享；持久化不遺失

#### 流程示意
```
請求 key → CachePenetrationProtectionCache.get(key)
  ├─ ① 空值快取命中？ → YES → 回傳 null (快取命中)
  └─ ② 正常快取查找 → 命中 → 回傳值 / 未命中 → DB → 寫入快取
                                                          ├─ result ≠ null → 寫入 Redis + BF.add(key)
                                                          └─ result = null  → 寫入空值標記 (short TTL)
```

### 快取雪崩防護（已實作）

採用三層防護機制：

#### 第一層：TTL 隨機化
- 所有 cache 的 TTL 在基礎值上加入 **0~30% 的亂數偏移**
- 例：`userProjects` 基礎 10 分鐘 → 實際 10~13 分鐘隨機
- 每個 key 獨立過期，不再集體失效
- 實作於 `RedisConfig.withJitter()`

#### 第二層：分散式鎖（Redis RLock）
- 當 `@Cacheable(sync=true)` 觸發 `get(key, valueLoader)` 時
- 使用 Redisson `RLock("lock:cache:{name}:{key}")` 進行分散式鎖定
- **只允許一個執行緒查 DB**，其他執行緒等待 200ms 後直接查 DB（防止死鎖）
- 跨 Docker 實例也有效
- 實作於 `CachePenetrationProtectionCache.get(key, valueLoader)`

#### 第三層：`@Cacheable(sync = true)`
- 所有 30 處 `@Cacheable` 已加入 `sync = true`
- 觸發 Spring Cache 的 `get(key, valueLoader)` 路徑
- 與分散式鎖配合，形成多層防護

#### 防護流程
```
多個請求同時打到同一過期的 key
  ├─ ① 只有第一個成功取得 RLock
  │    ├─ 雙重檢查快取（double-check）
  │    └─ valueLoader.call() 查 DB → 寫入快取 → 回傳
  ├─ ② 其他請求
  │    ├─ 鎖競爭失敗 → 等待 200ms
  │    └─ 雙重檢查快取 → 命中 → 回傳 (無 DB 查詢)
  └─ ③ 鎖超時 (200ms) → 降級直接查 DB（防止死鎖）
```

---

## 八、相關檔案說明

| 檔案 | 說明 |
|------|------|
| `Service/IBloomFilterService.java` | 布隆過濾器服務介面 |
| `Service/impl/BloomFilterService.java` | 布隆過濾器實作（Redisson RBloomFilter） |
| `Config/BloomFilterInitializer.java` | 啟動時從 DB 填充過濾器 |
| `Config/CachePenetrationProtectionCache.java` | 自訂 Cache 包裝（BF + Null Value） |
| `Config/CachePenetrationProtectionCacheManager.java` | 自訂 CacheManager |
| `Config/NullValueTtlProperties.java` | 空值 TTL 配置屬性 |
| `Util/NullValue.java` | 可序列化的空值佔位 POJO |

---

## 九、優先級與實作路線圖

| 階段 | 內容 | 影響範圍 | 狀態 |
|------|------|---------|:----:|
| **Phase 0 - 修復** | 修復 `UserService` + `AquarkDataService` key bug | 2 個 Service + 測試 | ✅ |
| **Phase 1 - 核心** | 實作 Skill、Role、Function 快取（參考資料） | 3 個 Service + RedisConfig | ✅ |
| **Phase 2 - 業務** | 實作 Company、JobPosting、Project 快取 | 3 個 Service | ✅ |
| **Phase 3 - 使用者** | 實作 UserJobLink、UserProjects、CurrentUserSkills 快取 | 3 個 Service | ✅ |
| **Phase 4 - 優化** | 實作 AquarkData 平均運算快取 | 1 個 Service | ✅ |
| **Phase 5 - 穿透防護** | Bloom Filter + Null Value 雙層穿透防護 | 9 Service + RedisConfig | ✅ |
| **Phase 6 - 雪崩防護** | TTL 隨機化 + 分散式鎖 + sync=true | 9 Service + RedisConfig + Cache | ✅ |
| **Phase 7 - 精確 Evict** | 以 `@CachePut` + 精確 key evict 取代全量清除 | 7 個 Service（全部） | ✅ |
| **Phase 8 - 監控整合** | Micrometer + Prometheus + Grafana | pom.xml + application.yml + docker-compose | ✅ |



---

## 十、清單快取安全性驗證（原始 List 不得直接存入 Redis）

### 檢查範圍

全專案所有 `@Cacheable` 方法中返回集合型別的快取，確保沒有未經包裝的 `List` 直接存入 Redis。

### 驗證結果

| 類別 | 數量 | 說明 |
|------|------|------|
| 透過 `CacheListWrapper<T>` 包裝的清單快取 | **20 個方法** | 正確使用包裝器，安全 ✅ |
| 返回 `PageResult<T>` 的快取（分頁物件，非純 List） | **9 個方法** | 天然序列化安全 ✅ |
| 直接儲存原始 `List<?>` 到 Redis 的快取 | **0 個方法** | **不存在** ✅ |
| 程式化 `cache.put(key, list)` 或 `redisTemplate.opsForValue().set(key, list)` | **0 處** | **不存在** ✅ |

### 已使用 `CacheListWrapper<T>` 的 20 個清單快取

| 模組 | 快取名稱 | Key 模式 | 返回型別 |
|------|---------|---------|---------|
| **project-skill** | `skills` | `'all'` | `CacheListWrapper<SkillVo>` |
| | `skillLevels` | `#skillId` | `CacheListWrapper<SkillLevelVo>` |
| | `currentUserSkills` | `'byuser:'+#currentUserId` | `CacheListWrapper<CurrentUserSkillVo>` |
| | `projects` | `'all'` | `CacheListWrapper<ProjectVo>` |
| | `projects` | `'byuser:'+#currentUserId` | `CacheListWrapper<ProjectVo>` |
| | `projectSkills` | `#projectId` | `CacheListWrapper<ProjectSkillVo>` |
| **job** | `jobPostings` | `'all'` | `CacheListWrapper<JobPostingVo>` |
| | `jobPostings` | `'bycompany:'+#companyId` | `CacheListWrapper<JobPostingVo>` |
| | `companies` | `'all'` | `CacheListWrapper<CompanyVo>` |
| | `userJobLinks` | `'all'` | `CacheListWrapper<UserJobLinkVo>` |
| | `userJobLinks` | `'byuser:'+#userId` | `CacheListWrapper<UserJobLinkVo>` |
| | `userJobLinks` | `'byjob:'+#jobPostingId` | `CacheListWrapper<UserJobLinkVo>` |
| | `userJobLinks` | `'currentuser:'+#currentUserId` | `CacheListWrapper<UserJobLinkVo>` |
| **iam** | `roles` | `'all'` | `CacheListWrapper<RoleOutVo>` |
| | `roleFunctions` | `#roleId` | `CacheListWrapper<FunctionVo>` |
| | `roles` | `'byfunction:'+#functionId` | `CacheListWrapper<RoleOutVo>` |
| | `userRoles` | `'byrole:'+#roleId` | `CacheListWrapper<UserVo>` |
| | `roles` | `'byuser:'+#userId` | `CacheListWrapper<RoleOutVo>` |
| | `functions` | `'all'` | `CacheListWrapper<FunctionVo>` |

### 實作細節

```java
// CacheListWrapper 定義（位於 backend-common）
public class CacheListWrapper<T> {
    private List<T> data;

    @JsonCreator
    public CacheListWrapper(@JsonProperty("data") List<T> data) {
        this.data = data;
    }

    public List<T> getData() {
        return data;
    }
}
```

### 設計原則

- 所有清單快取**必須**透過 `CacheListWrapper<T>` 或 `PageResult<T>` 包裝，禁止直接快取原始 `List` 物件
- `CacheListWrapper` 使用 Jackson `@JsonCreator` / `@JsonProperty` 註解確保序列化/反序列化正確性
- 後續新增清單快取時應遵循此模式，避免 `ClassCastException`
