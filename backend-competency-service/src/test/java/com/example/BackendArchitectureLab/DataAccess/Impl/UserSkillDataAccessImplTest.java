package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.Repository.UserSkillRepository;
import com.example.BackendArchitectureLab.Repository.SkillRepository;
import com.example.BackendArchitectureLab.Repository.SkillLevelRepository;
import com.example.BackendArchitectureLab.DataAccess.IUserSkillDataAccess;
import com.example.BackendArchitectureLab.Entity.UserSkill;
import com.example.BackendArchitectureLab.Entity.Skill;
import com.example.BackendArchitectureLab.Entity.SkillLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for UserSkillDataAccessImpl.
 * Uses in-memory H2 database for testing.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(UserSkillDataAccessImpl.class)
class UserSkillDataAccessImplTest {

    @Autowired
    private UserSkillRepository userSkillRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private SkillLevelRepository skillLevelRepository;

    @Autowired
    private IUserSkillDataAccess userSkillDataAccess;

    @BeforeEach
    void setUp() {
        userSkillRepository.deleteAll();
        skillRepository.deleteAll();
        skillLevelRepository.deleteAll();
    }

    private SkillLevel prepareSkillAndLevel(String skillName, String levelTitle) {
        Skill skill = new Skill();
        skill.setName(skillName);
        skillRepository.save(skill);

        SkillLevel skillLevel = new SkillLevel();
        skillLevel.setSkill(skill);
        skillLevel.setTitle(levelTitle);
        skillLevel.setLevelValue(1);
        skillLevelRepository.save(skillLevel);

        return skillLevel;
    }

    @Test
    @DisplayName("應該檢查 UserSkill 是否存在（依 userId 和 skillId）")
    void testExistsByUserIdAndSkillId() {
        // Arrange
        UUID userId = UUID.randomUUID();
        SkillLevel skillLevel = prepareSkillAndLevel("Java", "Default");
        Skill skill = skillLevel.getSkill();

        UserSkill userSkill = new UserSkill();
        userSkill.setUserId(userId);
        userSkill.setSkill(skill);
        userSkill.setSkillLevel(skillLevel);
        userSkillRepository.save(userSkill);

        // Act & Assert
        assertTrue(userSkillDataAccess.existsByUserIdAndSkillId(userId, skill.getId()));
        assertFalse(userSkillDataAccess.existsByUserIdAndSkillId(UUID.randomUUID(), skill.getId()));
    }

    @Test
    @DisplayName("應該檢查是否存在使用指定 SkillLevel 的 UserSkill")
    void testExistsBySkillLevelId() {
        // Arrange
        UUID userId = UUID.randomUUID();
        SkillLevel skillLevel = prepareSkillAndLevel("Java", "Junior");
        Skill skill = skillLevel.getSkill();

        UserSkill userSkill = new UserSkill();
        userSkill.setUserId(userId);
        userSkill.setSkill(skill);
        userSkill.setSkillLevel(skillLevel);
        userSkillRepository.save(userSkill);

        // Act & Assert
        assertTrue(userSkillDataAccess.existsBySkillLevelId(skillLevel.getId()));
        assertFalse(userSkillDataAccess.existsBySkillLevelId(UUID.randomUUID()));
    }

    @Test
    @DisplayName("應該保存 UserSkill")
    void testSave() {
        // Arrange
        UUID userId = UUID.randomUUID();
        SkillLevel skillLevel = prepareSkillAndLevel("Java", "Default");
        Skill skill = skillLevel.getSkill();

        UserSkill userSkill = new UserSkill();
        userSkill.setUserId(userId);
        userSkill.setSkill(skill);
        userSkill.setSkillLevel(skillLevel);

        // Act
        UserSkill saved = userSkillDataAccess.save(userSkill);

        // Assert
        assertNotNull(saved.getId());
        assertEquals(userId, saved.getUserId());
        assertEquals(skill.getId(), saved.getSkill().getId());
    }

    @Test
    @DisplayName("應該根據 skillId 刪除所有 UserSkill")
    void testDeleteBySkillId() {
        // Arrange
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        SkillLevel skillLevel = prepareSkillAndLevel("Java", "Default");
        Skill skill = skillLevel.getSkill();

        UserSkill userSkill1 = new UserSkill();
        userSkill1.setUserId(userId1);
        userSkill1.setSkill(skill);
        userSkill1.setSkillLevel(skillLevel);
        userSkillRepository.save(userSkill1);

        UserSkill userSkill2 = new UserSkill();
        userSkill2.setUserId(userId2);
        userSkill2.setSkill(skill);
        userSkill2.setSkillLevel(skillLevel);
        userSkillRepository.save(userSkill2);

        assertEquals(2, userSkillRepository.count());

        // Act
        userSkillDataAccess.deleteBySkillId(skill.getId());

        // Assert
        assertEquals(0, userSkillRepository.count());
    }

    @Test
    @DisplayName("應該根據 userId 查詢所有 UserSkill")
    void testFindByUserId() {
        // Arrange
        UUID userId = UUID.randomUUID();
        SkillLevel sl1 = prepareSkillAndLevel("Java", "Default");
        Skill skill1 = sl1.getSkill();
        SkillLevel sl2 = prepareSkillAndLevel("Python", "Default");
        Skill skill2 = sl2.getSkill();

        UserSkill userSkill1 = new UserSkill();
        userSkill1.setUserId(userId);
        userSkill1.setSkill(skill1);
        userSkill1.setSkillLevel(sl1);
        userSkillRepository.save(userSkill1);

        UserSkill userSkill2 = new UserSkill();
        userSkill2.setUserId(userId);
        userSkill2.setSkill(skill2);
        userSkill2.setSkillLevel(sl2);
        userSkillRepository.save(userSkill2);

        // Act
        List<UserSkill> result = userSkillDataAccess.findByUserId(userId);

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(us -> us.getSkill().getName().equals("Java")));
        assertTrue(result.stream().anyMatch(us -> us.getSkill().getName().equals("Python")));
    }

    @Test
    @DisplayName("當 userId 不存在時應該返回空列表")
    void testFindByUserId_NotFound() {
        // Act
        List<UserSkill> result = userSkillDataAccess.findByUserId(UUID.randomUUID());

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("應該檢查是否存在使用指定 Skill 的 UserSkill")
    void testExistsBySkillId() {
        UUID userId = UUID.randomUUID();
        SkillLevel skillLevel = prepareSkillAndLevel("Java", "Default");
        Skill skill = skillLevel.getSkill();

        UserSkill userSkill = new UserSkill();
        userSkill.setUserId(userId);
        userSkill.setSkill(skill);
        userSkill.setSkillLevel(skillLevel);
        userSkillRepository.save(userSkill);

        assertTrue(userSkillDataAccess.existsBySkillId(skill.getId()));
        assertFalse(userSkillDataAccess.existsBySkillId(UUID.randomUUID()));
    }

    @Test
    @DisplayName("應該根據 userId 和 skillId 刪除 UserSkill")
    void testDeleteByUserIdAndSkillId() {
        UUID userId = UUID.randomUUID();
        SkillLevel skillLevel = prepareSkillAndLevel("Java", "Default");
        Skill skill = skillLevel.getSkill();

        UserSkill userSkill = new UserSkill();
        userSkill.setUserId(userId);
        userSkill.setSkill(skill);
        userSkill.setSkillLevel(skillLevel);
        userSkillRepository.save(userSkill);

        assertEquals(1, userSkillRepository.count());

        userSkillDataAccess.deleteByUserIdAndSkillId(userId, skill.getId());

        assertEquals(0, userSkillRepository.count());
    }

    @Test
    @DisplayName("應該根據 skillId 查詢所有 UserSkill")
    void testFindBySkillId() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        SkillLevel skillLevel = prepareSkillAndLevel("Java", "Default");
        Skill skill = skillLevel.getSkill();

        UserSkill userSkill1 = new UserSkill();
        userSkill1.setUserId(userId1);
        userSkill1.setSkill(skill);
        userSkill1.setSkillLevel(skillLevel);
        userSkillRepository.save(userSkill1);

        UserSkill userSkill2 = new UserSkill();
        userSkill2.setUserId(userId2);
        userSkill2.setSkill(skill);
        userSkill2.setSkillLevel(skillLevel);
        userSkillRepository.save(userSkill2);

        List<UserSkill> result = userSkillDataAccess.findBySkillId(skill.getId());

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("應該根據 userId 和 skillId 查詢 UserSkill")
    void testFindByUserIdAndSkillId() {
        UUID userId = UUID.randomUUID();
        SkillLevel skillLevel = prepareSkillAndLevel("Java", "Default");
        Skill skill = skillLevel.getSkill();

        UserSkill userSkill = new UserSkill();
        userSkill.setUserId(userId);
        userSkill.setSkill(skill);
        userSkill.setSkillLevel(skillLevel);
        userSkillRepository.save(userSkill);

        List<UserSkill> result = userSkillDataAccess.findByUserIdAndSkillId(userId, skill.getId());

        assertEquals(1, result.size());
        assertEquals(userId, result.get(0).getUserId());
    }
}
