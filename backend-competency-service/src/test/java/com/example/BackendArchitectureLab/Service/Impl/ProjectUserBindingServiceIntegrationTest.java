package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectSkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.Impl.CompensationOutboxEventDataAccessImpl;
import com.example.BackendArchitectureLab.DataAccess.Impl.CompensationRestoreLogDataAccessImpl;
import com.example.BackendArchitectureLab.DataAccess.Impl.ProjectDataAccessImpl;
import com.example.BackendArchitectureLab.DataAccess.Impl.SkillDataAccessImpl;
import com.example.BackendArchitectureLab.DataAccess.Impl.SkillLevelDataAccessImpl;
import com.example.BackendArchitectureLab.DataAccess.Impl.UserProjectDataAccessImpl;
import com.example.BackendArchitectureLab.DataAccess.Impl.UserProjectSkillDataAccessImpl;
import com.example.BackendArchitectureLab.DataAccess.ISkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.ISkillLevelDataAccess;
import com.example.BackendArchitectureLab.Entity.CompensationRestoreLog;
import com.example.BackendArchitectureLab.Entity.Project;
import com.example.BackendArchitectureLab.Entity.Skill;
import com.example.BackendArchitectureLab.Entity.SkillLevel;
import com.example.BackendArchitectureLab.Entity.UserProject;
import com.example.BackendArchitectureLab.Entity.UserProjectSkill;
import com.example.BackendArchitectureLab.Exception.CompensationConflictException;
import com.example.BackendArchitectureLab.Repository.CompensationRestoreLogRepository;
import com.example.BackendArchitectureLab.Repository.ProjectRepository;
import com.example.BackendArchitectureLab.Service.ICompensationRestoreService;
import com.example.BackendArchitectureLab.Service.IUserGateway;
import com.example.BackendArchitectureLab.Vo.BindingSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * ProjectUserBindingService 補償還原閉環整合測試（competency-service 首個 @SpringBootTest，H2 真實 DB）：
 * <p>
 * 驗證 PR #46 修正後的完整生命週期：
 * 1. rebind 成功後 binding 持久化，restore 正確版本 → CompensationRestoreLog SUCCESS。
 * 2. restore 使用過期 expectedVersion → CompensationConflictException + FAILED，既有 binding 不被刪除。
 * 3. FAILED 認領紀錄可被更高 fencingVersion 接管並重試成功。
 * 4. malformed payload（缺欄位）→ IllegalArgumentException，破壞性 DELETE 不執行。
 */
@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = WebEnvironment.NONE,
        classes = ProjectUserBindingServiceIntegrationTest.CompensationTestApp.class,
        properties = {
                "app.init.enabled=false",
                "spring.autoconfigure.exclude="
                        + "org.redisson.spring.starter.RedissonAutoConfigurationV2,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.kafka.autoconfigure.KafkaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
                "spring.cloud.service-registry.auto-registration.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "external-sync.enabled=false"
        })
class ProjectUserBindingServiceIntegrationTest {

    @EnableAutoConfiguration
    @EnableJpaAuditing
    @EntityScan(basePackageClasses = Project.class)
    @EnableJpaRepositories(basePackageClasses = ProjectRepository.class)
    @Import({ProjectUserBindingService.class,
            CompensationRestoreService.class,
            CompensationOutboxEventDataAccessImpl.class,
            CompensationRestoreLogDataAccessImpl.class,
            ProjectDataAccessImpl.class,
            UserProjectDataAccessImpl.class,
            UserProjectSkillDataAccessImpl.class,
            SkillDataAccessImpl.class,
            SkillLevelDataAccessImpl.class,
            CompensationOutboxServiceImpl.class,
            ExternalSyncServiceImpl.class,
            TransactionalRestoreFacade.class})
    static class CompensationTestApp {

        @Bean
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }

    /**
     * 交易測試門面：在獨立交易內執行還原後強制拋出例外，模擬「commit 失敗」，
     用於驗證 SUCCESS 標記與還原資料的原子回滾。
     */
    @Service
    public static class TransactionalRestoreFacade {

        @Autowired
        private ICompensationRestoreService compensationRestoreService;

        @Transactional
        public void restoreThenRollback(UUID projectId, UUID eventId, Long expectedVersion,
                                        String ownerId, Long fencingVersion, List<BindingSnapshot> bindings) {
            compensationRestoreService.restoreMemberSkills(
                    projectId, eventId, expectedVersion, ownerId, fencingVersion, bindings);
            throw new RuntimeException("force rollback");
        }
    }

    @Autowired
    private ProjectUserBindingService projectUserBindingService;

    @Autowired
    private ICompensationRestoreService compensationRestoreService;

    @Autowired
    private TransactionalRestoreFacade restoreFacade;

    @Autowired
    private IProjectDataAccess projectDataAccess;

    @Autowired
    private IUserProjectDataAccess userProjectDataAccess;

    @Autowired
    private IUserProjectSkillDataAccess userProjectSkillDataAccess;

    @Autowired
    private ISkillDataAccess skillDataAccess;

    @Autowired
    private ISkillLevelDataAccess skillLevelDataAccess;

    @Autowired
    private CompensationRestoreLogRepository restoreLogRepository;

    @MockBean
    private IUserGateway userGateway;

    @Test
    void rebindThenRestore_shouldCompleteClosedLoop_withSuccessLog() {
        UUID userId = UUID.randomUUID();
        Project project = seedProject();
        Skill skill = seedSkill("Java");
        SkillLevel level = seedLevel(skill, 1, "Beginner");
        seedUserProject(project, userId);

        when(userGateway.existsUserById(userId)).thenReturn(true);

        projectUserBindingService.rebindProjectMemberSkills(
                project.getId(), Map.of(userId, Map.of(skill.getId(), level.getId())));

        List<UserProjectSkill> afterRebind = userProjectSkillDataAccess.findByProjectId(project.getId());
        assertEquals(1, afterRebind.size());

        Project fresh = projectDataAccess.findById(project.getId()).orElseThrow();
        UUID eventId = UUID.randomUUID();
        BindingSnapshot snapshot = new BindingSnapshot(userId, skill.getId(), level.getId());

        compensationRestoreService.restoreMemberSkills(
                project.getId(), eventId, fresh.getVersion(), "owner-1", 1L, List.of(snapshot));

        CompensationRestoreLog log = restoreLogRepository.findById(eventId).orElseThrow();
        assertEquals("SUCCESS", log.getStatus());
        assertEquals("owner-1", log.getOwnerId());
        assertEquals(1, userProjectSkillDataAccess.findByProjectId(project.getId()).size());
    }

    @Test
    void restoreWithStaleExpectedVersion_shouldMarkFailed_andKeepBindingsUntouched() {
        UUID userId = UUID.randomUUID();
        Project project = seedProject();
        Skill skill = seedSkill("Java");
        SkillLevel level1 = seedLevel(skill, 1, "Beginner");
        SkillLevel level2 = seedLevel(skill, 2, "Intermediate");
        seedUserProject(project, userId);

        when(userGateway.existsUserById(userId)).thenReturn(true);

        projectUserBindingService.rebindProjectMemberSkills(
                project.getId(), Map.of(userId, Map.of(skill.getId(), level1.getId())));
        assertEquals(1, userProjectSkillDataAccess.findByProjectId(project.getId()).size());

        UUID eventId = UUID.randomUUID();
        // 快照明細：level1 狀態
        BindingSnapshot snapshot = new BindingSnapshot(userId, skill.getId(), level1.getId());

        // 快照後發生並發 rebind，將 level 改為 level2 → 目前狀態與快照目標不符
        projectUserBindingService.rebindProjectMemberSkills(
                project.getId(), Map.of(userId, Map.of(skill.getId(), level2.getId())));
        assertEquals(1, userProjectSkillDataAccess.findByProjectId(project.getId()).size());

        // 使用過期的 expectedVersion（快照後被另一筆 rebind 更新），且目前狀態 ≠ 快照，應拒絕還原
        assertThrows(CompensationConflictException.class, () ->
                compensationRestoreService.restoreMemberSkills(
                        project.getId(), eventId, 0L, "owner-1", 1L, List.of(snapshot)));

        CompensationRestoreLog log = restoreLogRepository.findById(eventId).orElseThrow();
        assertEquals("FAILED", log.getStatus());
        assertTrue(log.getLastError() != null && log.getLastError().contains("newer modifications"));

        // 既有 binding 未被刪除（仍為 level2）
        assertEquals(1, userProjectSkillDataAccess.findByProjectId(project.getId()).size());
    }

    @Test
    void failedLog_shouldBeReclaimedByHigherFencingVersion_andRetrySucceeds() {
        UUID userId = UUID.randomUUID();
        Project project = seedProject();
        Skill skill = seedSkill("Java");
        SkillLevel level = seedLevel(skill, 1, "Beginner");
        seedUserProject(project, userId);

        when(userGateway.existsUserById(userId)).thenReturn(true);

        projectUserBindingService.rebindProjectMemberSkills(
                project.getId(), Map.of(userId, Map.of(skill.getId(), level.getId())));

        Project fresh = projectDataAccess.findById(project.getId()).orElseThrow();
        UUID eventId = UUID.randomUUID();
        BindingSnapshot snapshot = new BindingSnapshot(userId, skill.getId(), level.getId());

        // 第一次還原以過期版本 + 與現況不符的目標（空快照）失敗 → FAILED
        assertThrows(CompensationConflictException.class, () ->
                compensationRestoreService.restoreMemberSkills(
                        project.getId(), eventId, 0L, "owner-1", 1L, List.of()));
        assertEquals("FAILED", restoreLogRepository.findById(eventId).orElseThrow().getStatus());

        // 更高 fencingVersion + 正確版本接管後重試 → SUCCESS
        compensationRestoreService.restoreMemberSkills(
                project.getId(), eventId, fresh.getVersion(), "owner-2", 2L, List.of(snapshot));

        CompensationRestoreLog log = restoreLogRepository.findById(eventId).orElseThrow();
        assertEquals("SUCCESS", log.getStatus());
        assertEquals("owner-2", log.getOwnerId());
        assertEquals(2L, log.getFencingVersion());
        assertEquals(1, userProjectSkillDataAccess.findByProjectId(project.getId()).size());
    }

    @Test
    void restoreWithMalformedBinding_shouldThrowIllegalArgument_andNotDelete() {
        UUID userId = UUID.randomUUID();
        Project project = seedProject();
        Skill skill = seedSkill("Java");
        SkillLevel level = seedLevel(skill, 1, "Beginner");
        seedUserProject(project, userId);

        when(userGateway.existsUserById(userId)).thenReturn(true);

        projectUserBindingService.rebindProjectMemberSkills(
                project.getId(), Map.of(userId, Map.of(skill.getId(), level.getId())));
        assertEquals(1, userProjectSkillDataAccess.findByProjectId(project.getId()).size());

        Project fresh = projectDataAccess.findById(project.getId()).orElseThrow();
        UUID eventId = UUID.randomUUID();

        // malformed payload：levelId 為 null，DELETE 前驗證應拋出 IllegalArgumentException
        BindingSnapshot malformed = new BindingSnapshot(userId, skill.getId(), null);
        assertThrows(IllegalArgumentException.class, () ->
                compensationRestoreService.restoreMemberSkills(
                        project.getId(), eventId, fresh.getVersion(), "owner-1", 1L, List.of(malformed)));

        // 既有 binding 未被刪除（DELETE 未執行）
        assertEquals(1, userProjectSkillDataAccess.findByProjectId(project.getId()).size());
        assertEquals("FAILED", restoreLogRepository.findById(eventId).orElseThrow().getStatus());
    }

    @Test
    void restoreRollback_shouldNotLeaveSuccessLog_orTouchBindings() {
        UUID userId = UUID.randomUUID();
        Project project = seedProject();
        Skill skill = seedSkill("Java");
        SkillLevel level = seedLevel(skill, 1, "Beginner");
        seedUserProject(project, userId);

        UserProjectSkill existing = new UserProjectSkill();
        existing.setUserId(userId);
        existing.setProject(project);
        existing.setSkill(skill);
        existing.setSkillLevel(level);
        userProjectSkillDataAccess.save(existing);
        assertEquals(1, userProjectSkillDataAccess.findByProjectId(project.getId()).size());

        Project fresh = projectDataAccess.findById(project.getId()).orElseThrow();
        UUID eventId = UUID.randomUUID();
        BindingSnapshot snapshot = new BindingSnapshot(userId, skill.getId(), level.getId());

        // commit 失敗（交易強制回滾）：SUCCESS 標記與還原資料應一併原子回滾。
        // 認領（PROCESSING）為 REQUIRES_NEW 先獨立 commit 故仍存在，但 SUCCESS 不得殘留
        assertThrows(RuntimeException.class, () ->
                restoreFacade.restoreThenRollback(
                        project.getId(), eventId, fresh.getVersion(), "owner-1", 1L, List.of(snapshot)));

        CompensationRestoreLog log = restoreLogRepository.findById(eventId).orElseThrow();
        assertEquals("PROCESSING", log.getStatus());
        assertEquals(1, userProjectSkillDataAccess.findByProjectId(project.getId()).size());
    }

    @Test
    void leftoverProcessingLog_shouldReconcileToSuccess_whenBindingsAlreadyRestored() {
        UUID userId = UUID.randomUUID();
        Project project = seedProject();
        Skill skill = seedSkill("Java");
        SkillLevel level = seedLevel(skill, 1, "Beginner");
        seedUserProject(project, userId);

        // 模擬 C-01 crash window 殘留：先前 restore 資料已 commit 成功（binding 已還原），
        // 但 SUCCESS 標記遺失（log 停留 PROCESSING 且租約已過期）
        UserProjectSkill restored = new UserProjectSkill();
        restored.setUserId(userId);
        restored.setProject(project);
        restored.setSkill(skill);
        restored.setSkillLevel(level);
        userProjectSkillDataAccess.save(restored);

        UUID eventId = UUID.randomUUID();
        CompensationRestoreLog leftover = new CompensationRestoreLog();
        leftover.setEventId(eventId);
        leftover.setProjectId(project.getId());
        leftover.setProcessedAt(new Date());
        leftover.setStatus("PROCESSING");
        leftover.setOwnerId("old-owner");
        leftover.setFencingVersion(1L);
        leftover.setLeaseUntil(new Date(System.currentTimeMillis() - 60_000L));
        restoreLogRepository.saveAndFlush(leftover);

        // reclaim 以更高 fencingVersion 接管；expectedVersion 過期（第一次還原已 bump version），
        // 但 bindings 已等於目標 → 冪等比對不重跑還原，直接標記 SUCCESS（Option B）
        BindingSnapshot snapshot = new BindingSnapshot(userId, skill.getId(), level.getId());
        compensationRestoreService.restoreMemberSkills(
                project.getId(), eventId, 0L, "new-owner", 2L, List.of(snapshot));

        CompensationRestoreLog log = restoreLogRepository.findById(eventId).orElseThrow();
        assertEquals("SUCCESS", log.getStatus());
        assertEquals("new-owner", log.getOwnerId());
        assertEquals(2L, log.getFencingVersion());
        assertEquals(1, userProjectSkillDataAccess.findByProjectId(project.getId()).size());
    }

    private Project seedProject() {
        Project project = new Project();
        project.setName("Integration Test Project");
        project.setDescription("desc");
        return projectDataAccess.save(project);
    }

    private Skill seedSkill(String name) {
        Skill skill = new Skill();
        skill.setName(name);
        return skillDataAccess.save(skill);
    }

    private SkillLevel seedLevel(Skill skill, Integer value, String title) {
        SkillLevel level = new SkillLevel();
        level.setSkill(skill);
        level.setLevelValue(value);
        level.setTitle(title);
        return skillLevelDataAccess.save(level);
    }

    private void seedUserProject(Project project, UUID userId) {
        UserProject userProject = new UserProject();
        userProject.setUserId(userId);
        userProject.setProject(project);
        userProjectDataAccess.save(userProject);
    }
}
