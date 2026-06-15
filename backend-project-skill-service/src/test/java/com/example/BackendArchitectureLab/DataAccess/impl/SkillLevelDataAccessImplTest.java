package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.DataAccess.ISkillLevelDataAccess;
import com.example.BackendArchitectureLab.DataAccess.impl.SkillLevelDataAccessImpl;
import com.example.BackendArchitectureLab.Dto.Vo.Search.SkillLevelSearchQuery;
import com.example.BackendArchitectureLab.Entity.Skill;
import com.example.BackendArchitectureLab.Entity.SkillLevel;
import com.example.BackendArchitectureLab.Repository.SkillLevelRepository;
import com.example.BackendArchitectureLab.Repository.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for SkillLevelDataAccessImpl.
 * Uses in-memory H2 database for testing.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(SkillLevelDataAccessImpl.class)
class SkillLevelDataAccessImplTest {

    @Autowired
    private ISkillLevelDataAccess skillLevelDataAccess;

    @Autowired
    private SkillLevelRepository skillLevelRepository;

    @Autowired
    private SkillRepository skillRepository;

    @BeforeEach
    void setUp() {
        skillLevelRepository.deleteAll();
        skillRepository.deleteAll();
    }

    @Test
    @DisplayName("應該保存 SkillLevel")
    void testSave() {
        // Arrange
        Skill skill = new Skill();
        skill.setName("Java");
        skillRepository.save(skill);

        SkillLevel skillLevel = new SkillLevel();
        skillLevel.setSkill(skill);
        skillLevel.setTitle("Junior");
        skillLevel.setLevelValue(1);

        // Act
        SkillLevel saved = skillLevelDataAccess.save(skillLevel);

        // Assert
        assertNotNull(saved.getId());
        assertEquals("Junior", saved.getTitle());
        assertEquals(1, saved.getLevelValue());
        assertEquals(skill.getId(), saved.getSkill().getId());
    }

    @Test
    @DisplayName("應該根據 ID 查詢 SkillLevel")
    void testFindById() {
        // Arrange
        Skill skill = new Skill();
        skill.setName("Java");
        skillRepository.save(skill);

        SkillLevel skillLevel = new SkillLevel();
        skillLevel.setSkill(skill);
        skillLevel.setTitle("Junior");
        skillLevel.setLevelValue(1);
        skillLevelRepository.save(skillLevel);

        // Act
        Optional<SkillLevel> result = skillLevelDataAccess.findById(skillLevel.getId());

        // Assert
        assertTrue(result.isPresent());
        assertEquals("Junior", result.get().getTitle());
    }

    @Test
    @DisplayName("當 ID 不存在時應該返回 empty Optional")
    void testFindById_NotFound() {
        // Act
        Optional<SkillLevel> result = skillLevelDataAccess.findById(UUID.randomUUID());

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("應該根據 skillId 查詢所有 SkillLevel 並依 levelValue 升序排序")
    void testFindBySkillIdOrderByLevelValueAsc() {
        // Arrange
        Skill skill = new Skill();
        skill.setName("Java");
        skillRepository.save(skill);

        SkillLevel level1 = new SkillLevel();
        level1.setSkill(skill);
        level1.setTitle("Senior");
        level1.setLevelValue(3);
        skillLevelRepository.save(level1);

        SkillLevel level2 = new SkillLevel();
        level2.setSkill(skill);
        level2.setTitle("Junior");
        level2.setLevelValue(1);
        skillLevelRepository.save(level2);

        SkillLevel level3 = new SkillLevel();
        level3.setSkill(skill);
        level3.setTitle("Mid");
        level3.setLevelValue(2);
        skillLevelRepository.save(level3);

        // Act
        List<SkillLevel> result = skillLevelDataAccess.findBySkillIdOrderByLevelValueAsc(skill.getId());

        // Assert
        assertEquals(3, result.size());
        assertEquals("Junior", result.get(0).getTitle());
        assertEquals("Mid", result.get(1).getTitle());
        assertEquals("Senior", result.get(2).getTitle());
    }

    @Test
    @DisplayName("應該檢查是否存在指定 skillId 和 levelValue 的 SkillLevel")
    void testExistsBySkillIdAndLevelValue() {
        // Arrange
        Skill skill = new Skill();
        skill.setName("Java");
        skillRepository.save(skill);

        SkillLevel skillLevel = new SkillLevel();
        skillLevel.setSkill(skill);
        skillLevel.setTitle("Junior");
        skillLevel.setLevelValue(1);
        skillLevelRepository.save(skillLevel);

        // Act & Assert
        assertTrue(skillLevelDataAccess.existsBySkillIdAndLevelValue(skill.getId(), 1));
        assertFalse(skillLevelDataAccess.existsBySkillIdAndLevelValue(skill.getId(), 2));
        assertFalse(skillLevelDataAccess.existsBySkillIdAndLevelValue(UUID.randomUUID(), 1));
    }

    @Test
    @DisplayName("應該刪除 SkillLevel")
    void testDelete() {
        // Arrange
        Skill skill = new Skill();
        skill.setName("Java");
        skillRepository.save(skill);

        SkillLevel skillLevel = new SkillLevel();
        skillLevel.setSkill(skill);
        skillLevel.setTitle("Junior");
        skillLevel.setLevelValue(1);
        skillLevelRepository.save(skillLevel);

        assertEquals(1, skillLevelRepository.count());

        // Act
        skillLevelDataAccess.delete(skillLevel);

        // Assert
        assertEquals(0, skillLevelRepository.count());
    }

    @Test
    @DisplayName("應該根據 skillId 刪除所有 SkillLevel")
    void testDeleteBySkillId() {
        // Arrange
        Skill skill = new Skill();
        skill.setName("Java");
        skillRepository.save(skill);

        SkillLevel level1 = new SkillLevel();
        level1.setSkill(skill);
        level1.setTitle("Junior");
        level1.setLevelValue(1);
        skillLevelRepository.save(level1);

        SkillLevel level2 = new SkillLevel();
        level2.setSkill(skill);
        level2.setTitle("Senior");
        level2.setLevelValue(3);
        skillLevelRepository.save(level2);

        assertEquals(2, skillLevelRepository.count());

        // Act
        skillLevelDataAccess.deleteBySkillId(skill.getId());

        // Assert
        assertEquals(0, skillLevelRepository.count());
    }

    @Test
    @DisplayName("應該根據條件查詢 SkillLevel（分頁）")
    void testSearchSkillLevels() {
        // Arrange
        Skill skill = new Skill();
        skill.setName("Java");
        skillRepository.save(skill);

        SkillLevel level1 = new SkillLevel();
        level1.setSkill(skill);
        level1.setTitle("Junior");
        level1.setLevelValue(1);
        skillLevelRepository.save(level1);

        SkillLevel level2 = new SkillLevel();
        level2.setSkill(skill);
        level2.setTitle("Senior");
        level2.setLevelValue(3);
        skillLevelRepository.save(level2);

        SkillLevelSearchQuery query = new SkillLevelSearchQuery();
        query.setSkillId(skill.getId());
        query.setPage(0);
        query.setSize(10);
        query.setSortBy("levelValue");
        query.setSortDir("asc");

        // Act
        Page<SkillLevel> result = skillLevelDataAccess.searchSkillLevels(query);

        // Assert
        assertEquals(2, result.getTotalElements());
        assertEquals(2, result.getContent().size());
        assertEquals("Junior", result.getContent().get(0).getTitle());
        assertEquals("Senior", result.getContent().get(1).getTitle());
    }

    @Test
    @DisplayName("應該根據條件查詢 SkillLevel（分頁，降序）")
    void testSearchSkillLevels_Descending() {
        // Arrange
        Skill skill = new Skill();
        skill.setName("Java");
        skillRepository.save(skill);

        SkillLevel level1 = new SkillLevel();
        level1.setSkill(skill);
        level1.setTitle("Junior");
        level1.setLevelValue(1);
        skillLevelRepository.save(level1);

        SkillLevel level2 = new SkillLevel();
        level2.setSkill(skill);
        level2.setTitle("Senior");
        level2.setLevelValue(3);
        skillLevelRepository.save(level2);

        SkillLevelSearchQuery query = new SkillLevelSearchQuery();
        query.setSkillId(skill.getId());
        query.setPage(0);
        query.setSize(10);
        query.setSortBy("levelValue");
        query.setSortDir("desc");

        // Act
        Page<SkillLevel> result = skillLevelDataAccess.searchSkillLevels(query);

        // Assert
        assertEquals(2, result.getTotalElements());
        assertEquals("Senior", result.getContent().get(0).getTitle());
        assertEquals("Junior", result.getContent().get(1).getTitle());
    }
}
