package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.DataAccess.ISkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.impl.SkillDataAccessImpl;
import com.example.BackendArchitectureLab.Dto.Vo.Search.SkillSearchQuery;
import com.example.BackendArchitectureLab.Entity.Skill;
import com.example.BackendArchitectureLab.Repository.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for SkillDataAccessImpl.
 * Uses in-memory H2 database for testing.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(SkillDataAccessImpl.class)
class SkillDataAccessImplTest {

    @Autowired
    private ISkillDataAccess skillDataAccess;

    @Autowired
    private SkillRepository skillRepository;

    @BeforeEach
    void setUp() {
        skillRepository.deleteAll();
    }

    @Test
    @DisplayName("Should save a new skill")
    void testSave_NewSkill() {
        // Arrange
        Skill skill = new Skill();
        skill.setName("Java Programming");
        skill.setDescription("Java development skills");
        skill.setCreatedBy("admin");
        skill.setCreatedTime(new Date());

        // Act
        Skill savedSkill = skillDataAccess.save(skill);

        // Assert
        assertNotNull(savedSkill);
        assertNotNull(savedSkill.getId());
        assertEquals("Java Programming", savedSkill.getName());
        assertEquals("Java development skills", savedSkill.getDescription());
        assertEquals("admin", savedSkill.getCreatedBy());
    }

    @Test
    @DisplayName("Should update an existing skill")
    void testSave_UpdateSkill() {
        // Arrange
        Skill skill = new Skill();
        skill.setName("Python Programming");
        skill.setDescription("Python basics");
        skill.setCreatedBy("admin");
        skill.setCreatedTime(new Date());
        Skill savedSkill = skillRepository.save(skill);

        // Act
        savedSkill.setDescription("Advanced Python programming");
        savedSkill.setUpdatedBy("admin2");
        savedSkill.setUpdatedTime(new Date());
        Skill updatedSkill = skillDataAccess.save(savedSkill);

        // Assert
        assertNotNull(updatedSkill);
        assertEquals(savedSkill.getId(), updatedSkill.getId());
        assertEquals("Advanced Python programming", updatedSkill.getDescription());
        assertEquals("admin2", updatedSkill.getUpdatedBy());
    }

    @Test
    @DisplayName("Should return true when skill exists matching example")
    void testExists_Found() {
        // Arrange
        Skill skill = new Skill();
        skill.setName("JavaScript");
        skill.setDescription("JS development");
        skillRepository.save(skill);

        Skill probe = new Skill();
        probe.setName("JavaScript");
        Example<Skill> example = Example.of(probe);

        // Act
        boolean exists = skillDataAccess.exists(example);

        // Assert
        assertTrue(exists);
    }

    @Test
    @DisplayName("Should return false when skill does not exist matching example")
    void testExists_NotFound() {
        // Arrange
        Skill probe = new Skill();
        probe.setName("NonExistentSkill");
        Example<Skill> example = Example.of(probe);

        // Act
        boolean exists = skillDataAccess.exists(example);

        // Assert
        assertFalse(exists);
    }

    @Test
    @DisplayName("Should find all skills")
    void testFindAll() {
        // Arrange
        Skill skill1 = new Skill();
        skill1.setName("React");
        skill1.setDescription("React framework");
        skillRepository.save(skill1);

        Skill skill2 = new Skill();
        skill2.setName("Angular");
        skill2.setDescription("Angular framework");
        skillRepository.save(skill2);

        Skill skill3 = new Skill();
        skill3.setName("Vue");
        skill3.setDescription("Vue framework");
        skillRepository.save(skill3);

        // Act
        List<Skill> skills = skillDataAccess.findAll();

        // Assert
        assertEquals(3, skills.size());
        assertTrue(skills.stream().anyMatch(s -> s.getName().equals("React")));
        assertTrue(skills.stream().anyMatch(s -> s.getName().equals("Angular")));
        assertTrue(skills.stream().anyMatch(s -> s.getName().equals("Vue")));
    }

    @Test
    @DisplayName("Should return empty list when no skills exist")
    void testFindAll_Empty() {
        // Act
        List<Skill> skills = skillDataAccess.findAll();

        // Assert
        assertTrue(skills.isEmpty());
    }

    @Test
    @DisplayName("Should find skill by ID")
    void testFindById_Found() {
        // Arrange
        Skill skill = new Skill();
        skill.setName("Docker");
        skill.setDescription("Containerization");
        Skill savedSkill = skillRepository.save(skill);

        // Act
        Optional<Skill> result = skillDataAccess.findById(savedSkill.getId());

        // Assert
        assertTrue(result.isPresent());
        assertEquals("Docker", result.get().getName());
        assertEquals("Containerization", result.get().getDescription());
    }

    @Test
    @DisplayName("Should return empty optional when skill ID not found")
    void testFindById_NotFound() {
        // Arrange
        UUID nonExistentId = UUID.randomUUID();

        // Act
        Optional<Skill> result = skillDataAccess.findById(nonExistentId);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should delete a skill")
    void testDelete() {
        // Arrange
        Skill skill = new Skill();
        skill.setName("Kubernetes");
        skill.setDescription("Container orchestration");
        Skill savedSkill = skillRepository.save(skill);
        UUID skillId = savedSkill.getId();

        // Act
        skillDataAccess.delete(savedSkill);

        // Assert
        Optional<Skill> result = skillRepository.findById(skillId);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should handle delete of non-existent skill gracefully")
    void testDelete_NonExistent() {
        // Arrange
        Skill skill = new Skill();
        skill.setId(UUID.randomUUID());
        skill.setName("NonExistent");

        // Act & Assert - should not throw Exception
        assertDoesNotThrow(() -> skillDataAccess.delete(skill));
    }
    
    @Test
    @DisplayName("Should search skills with query")
    void testSearchSkills() {
        // Arrange
        Skill skill1 = new Skill();
        skill1.setName("Java");
        skill1.setDescription("Java programming language");
        skillRepository.save(skill1);
        
        Skill skill2 = new Skill();
        skill2.setName("JavaScript");
        skill2.setDescription("JavaScript programming");
        skillRepository.save(skill2);
        
        Skill skill3 = new Skill();
        skill3.setName("Python");
        skill3.setDescription("Python language");
        skillRepository.save(skill3);
        
        SkillSearchQuery query = new SkillSearchQuery();
        query.setPage(0);
        query.setSize(10);
        query.setSortBy("name");
        query.setSortDir("asc");
        query.setName("Java");
        
        // Act
        Page<Skill> result = skillDataAccess.searchSkills(query);
        
        // Assert
        assertEquals(2, result.getTotalElements());
        assertTrue(result.getContent().stream().anyMatch(s -> s.getName().equals("Java")));
        assertTrue(result.getContent().stream().anyMatch(s -> s.getName().equals("JavaScript")));
    }
    
    @Test
    @DisplayName("Should search skills with pagination")
    void testSearchSkills_Pagination() {
        // Arrange
        for (int i = 0; i < 5; i++) {
            Skill skill = new Skill();
            skill.setName("Skill " + i);
            skill.setDescription("Description " + i);
            skillRepository.save(skill);
        }
        
        SkillSearchQuery query = new SkillSearchQuery();
        query.setPage(0);
        query.setSize(2);
        query.setSortBy("name");
        query.setSortDir("asc");
        
        // Act
        Page<Skill> result = skillDataAccess.searchSkills(query);
        
        // Assert
        assertEquals(5, result.getTotalElements());
        assertEquals(3, result.getTotalPages());
        assertEquals(2, result.getContent().size());
    }
    
    @Test
    @DisplayName("Should search skills in descending order")
    void testSearchSkills_DescendingOrder() {
        // Arrange
        Skill skill1 = new Skill();
        skill1.setName("AAA Skill");
        skillRepository.save(skill1);
        
        Skill skill2 = new Skill();
        skill2.setName("ZZZ Skill");
        skillRepository.save(skill2);
        
        SkillSearchQuery query = new SkillSearchQuery();
        query.setPage(0);
        query.setSize(10);
        query.setSortBy("name");
        query.setSortDir("desc");
        
        // Act
        Page<Skill> result = skillDataAccess.searchSkills(query);
        
        // Assert
        assertEquals(2, result.getTotalElements());
        assertEquals("ZZZ Skill", result.getContent().get(0).getName());
        assertEquals("AAA Skill", result.getContent().get(1).getName());
    }
    
    @Test
    @DisplayName("Should search skills with description Filter")
    void testSearchSkills_DescriptionFilter() {
        // Arrange
        Skill skill1 = new Skill();
        skill1.setName("Skill 1");
        skill1.setDescription("Backend development");
        skillRepository.save(skill1);
        
        Skill skill2 = new Skill();
        skill2.setName("Skill 2");
        skill2.setDescription("Frontend design");
        skillRepository.save(skill2);
        
        SkillSearchQuery query = new SkillSearchQuery();
        query.setPage(0);
        query.setSize(10);
        query.setSortBy("name");
        query.setSortDir("asc");
        query.setDescription("Backend");
        
        // Act
        Page<Skill> result = skillDataAccess.searchSkills(query);
        
        // Assert
        assertEquals(1, result.getTotalElements());
        assertEquals("Skill 1", result.getContent().get(0).getName());
    }
}
