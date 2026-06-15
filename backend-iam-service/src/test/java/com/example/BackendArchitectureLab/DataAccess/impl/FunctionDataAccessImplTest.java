package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.Repository.FunctionRepository;
import com.example.BackendArchitectureLab.DataAccess.IFunctionDataAccess;
import com.example.BackendArchitectureLab.Entity.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for FunctionDataAccessImpl.
 * Uses in-memory H2 database for testing.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(FunctionDataAccessImpl.class)
class FunctionDataAccessImplTest {

    @Autowired
    private FunctionRepository functionRepository;

    @Autowired
    private IFunctionDataAccess functionDataAccess;

    @BeforeEach
    void setUp() {
        functionRepository.deleteAll();
    }

    @Test
    @DisplayName("Should find all functions by IDs")
    void testFindAllById() {
        // Arrange
        Function func1 = new Function();
        func1.setName("Function1");
        func1.setParent("");
        functionRepository.save(func1);

        Function func2 = new Function();
        func2.setName("Function2");
        func2.setParent("");
        functionRepository.save(func2);

        List<UUID> ids = List.of(func1.getId(), func2.getId());

        // Act
        List<Function> result = functionDataAccess.findAllById(ids);

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(f -> f.getName().equals("Function1")));
        assertTrue(result.stream().anyMatch(f -> f.getName().equals("Function2")));
    }

    @Test
    @DisplayName("Should return empty list when no functions match IDs")
    void testFindAllById_NotFound() {
        // Arrange
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());

        // Act
        List<Function> result = functionDataAccess.findAllById(ids);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should find subset of functions when only some IDs match")
    void testFindAllById_PartialMatch() {
        // Arrange
        Function func1 = new Function();
        func1.setName("Function1");
        func1.setParent("");
        functionRepository.save(func1);

        List<UUID> ids = List.of(func1.getId(), UUID.randomUUID());

        // Act
        List<Function> result = functionDataAccess.findAllById(ids);

        // Assert
        assertEquals(1, result.size());
        assertEquals("Function1", result.get(0).getName());
    }

    @Test
    @DisplayName("Should handle empty ID list")
    void testFindAllById_EmptyList() {
        // Act
        List<Function> result = functionDataAccess.findAllById(List.of());

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should find functions with parent relationships")
    void testFindAllById_WithParents() {
        // Arrange
        Function parent = new Function();
        parent.setName("ParentFunction");
        parent.setParent("");
        functionRepository.save(parent);

        Function child = new Function();
        child.setName("ChildFunction");
        child.setParent(parent.getId().toString());
        functionRepository.save(child);

        // Act
        List<Function> result = functionDataAccess.findAllById(List.of(child.getId()));

        // Assert
        assertEquals(1, result.size());
        assertEquals("ChildFunction", result.get(0).getName());
        assertEquals(parent.getId().toString(), result.get(0).getParent());
    }

    @Test
    @DisplayName("Should save a function")
    void testSave() {
        // Arrange
        Function func = new Function();
        func.setName("Test Function");
        func.setParent("");
        func.setType(1);

        // Act
        Function saved = functionDataAccess.save(func);

        // Assert
        assertNotNull(saved.getId());
        assertEquals("Test Function", saved.getName());
        assertEquals(1, saved.getType());
    }

    @Test
    @DisplayName("Should check if function exists by example")
    void testExists() {
        // Arrange
        Function func = new Function();
        func.setName("Existing Function");
        functionRepository.save(func);

        Function probe = new Function();
        probe.setName("Existing Function");
        Example<Function> example = Example.of(probe);

        // Act
        boolean exists = functionDataAccess.exists(example);

        // Assert
        assertTrue(exists);
    }

    @Test
    @DisplayName("Should return false when function does not exist")
    void testExists_NotFound() {
        // Arrange
        Function probe = new Function();
        probe.setName("Non-existent Function");
        Example<Function> example = Example.of(probe);

        // Act
        boolean exists = functionDataAccess.exists(example);

        // Assert
        assertFalse(exists);
    }

    @Test
    @DisplayName("Should find all functions")
    void testFindAll() {
        // Arrange
        Function func1 = new Function();
        func1.setName("Function1");
        functionRepository.save(func1);

        Function func2 = new Function();
        func2.setName("Function2");
        functionRepository.save(func2);

        // Act
        List<Function> result = functionDataAccess.findAll();

        // Assert
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("Should find all functions with sorting")
    void testFindAllWithSort() {
        // Arrange
        Function func1 = new Function();
        func1.setName("B Function");
        func1.setSort("2");
        functionRepository.save(func1);

        Function func2 = new Function();
        func2.setName("A Function");
        func2.setSort("1");
        functionRepository.save(func2);

        Sort sort = Sort.by(Sort.Direction.ASC, "sort");

        // Act
        List<Function> result = functionDataAccess.findAll(sort);

        // Assert
        assertEquals(2, result.size());
        assertEquals("1", result.get(0).getSort());
        assertEquals("2", result.get(1).getSort());
    }

    @Test
    @DisplayName("Should delete a function")
    void testDelete() {
        // Arrange
        Function func = new Function();
        func.setName("To Delete");
        Function saved = functionRepository.save(func);

        // Act
        functionDataAccess.delete(saved);

        // Assert
        Optional<Function> found = functionRepository.findById(saved.getId());
        assertFalse(found.isPresent());
    }

    @Test
    @DisplayName("Should save all functions")
    void testSaveAll() {
        // Arrange
        Function func1 = new Function();
        func1.setName("Function1");
        Function func2 = new Function();
        func2.setName("Function2");
        List<Function> functions = List.of(func1, func2);

        // Act
        List<Function> saved = functionDataAccess.saveAll(functions);

        // Assert
        assertEquals(2, saved.size());
        assertNotNull(saved.get(0).getId());
        assertNotNull(saved.get(1).getId());
    }

    @Test
    @DisplayName("Should delete all functions")
    void testDeleteAll() {
        // Arrange
        Function func1 = new Function();
        func1.setName("Function1");
        Function func2 = new Function();
        func2.setName("Function2");
        List<Function> saved = functionRepository.saveAll(List.of(func1, func2));

        // Act
        functionDataAccess.deleteAll(saved);

        // Assert
        List<Function> remaining = functionRepository.findAll();
        assertTrue(remaining.isEmpty());
    }

    @Test
    @DisplayName("Should find functions by grand parent ID")
    void testFindAllByGrandParentId() {
        // Arrange
        String grandParentId = "grand-parent-id";

        Function func1 = new Function();
        func1.setName("Child1");
        func1.setParent(grandParentId);
        functionRepository.save(func1);

        Function func2 = new Function();
        func2.setName("Child2");
        func2.setParent(grandParentId);
        functionRepository.save(func2);

        Function func3 = new Function();
        func3.setName("Other");
        func3.setParent("other-parent");
        functionRepository.save(func3);

        // Act
        List<Function> result = functionDataAccess.findAllByGrandParentId(List.of(grandParentId));

        // Assert
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(f -> f.getParent().equals(grandParentId)));
    }

    @Test
    @DisplayName("Should find function by name")
    void testFindFunctionByName() {
        // Arrange
        Function func = new Function();
        func.setName("Unique Function");
        functionRepository.save(func);

        // Act
        Function result = functionDataAccess.findFunctionByName("Unique Function");

        // Assert
        assertNotNull(result);
        assertEquals("Unique Function", result.getName());
    }

    @Test
    @DisplayName("Should return null when function not found by name")
    void testFindFunctionByName_NotFound() {
        // Act
        Function result = functionDataAccess.findFunctionByName("Non-existent");

        // Assert
        assertNull(result);
    }

    @Test
    @DisplayName("Should find functions by name and parent")
    void testFindFunctionByNameAndParent() {
        // Arrange
        String parentId = "parent-id";

        Function func1 = new Function();
        func1.setName("Same Name");
        func1.setParent(parentId);
        functionRepository.save(func1);

        Function func2 = new Function();
        func2.setName("Same Name");
        func2.setParent("other-parent");
        functionRepository.save(func2);

        // Act
        List<Function> result = functionDataAccess.findFunctionByNameAndParent("Same Name", parentId);

        // Assert
        assertEquals(1, result.size());
        assertEquals(parentId, result.get(0).getParent());
    }

    @Test
    @DisplayName("Should return empty list when no functions match name and parent")
    void testFindFunctionByNameAndParent_NotFound() {
        // Act
        List<Function> result = functionDataAccess.findFunctionByNameAndParent("Non-existent", "parent-id");

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should find function by ID")
    void testFindById() {
        // Arrange
        Function func = new Function();
        func.setName("Test Function");
        Function saved = functionRepository.save(func);

        // Act
        Optional<Function> result = functionDataAccess.findById(saved.getId());

        // Assert
        assertTrue(result.isPresent());
        assertEquals("Test Function", result.get().getName());
    }

    @Test
    @DisplayName("Should return empty when function not found by ID")
    void testFindById_NotFound() {
        // Act
        Optional<Function> result = functionDataAccess.findById(UUID.randomUUID());

        // Assert
        assertFalse(result.isPresent());
    }
}

