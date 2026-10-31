package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.Repository.RoleFunctionRepository;
import com.example.BackendArchitectureLab.DataAccess.IRoleFunctionDataAccess;
import com.example.BackendArchitectureLab.Entity.Function;
import com.example.BackendArchitectureLab.Entity.Role;
import com.example.BackendArchitectureLab.Entity.RoleFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Example;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for RoleFunctionDataAccessImpl.
 * Uses in-memory H2 database for testing.
 */
@DataJpaTest
@ActiveProfiles("test")
class RoleFunctionDataAccessImplTest {

    private final TestEntityManager entityManager;
    private final RoleFunctionRepository roleFunctionRepository;
    private final IRoleFunctionDataAccess roleFunctionDataAccess;

    @Autowired
    public RoleFunctionDataAccessImplTest(TestEntityManager entityManager,
                                          RoleFunctionRepository roleFunctionRepository) {
        this.entityManager = entityManager;
        this.roleFunctionRepository = roleFunctionRepository;
        this.roleFunctionDataAccess = new RoleFunctionDataAccessImpl(roleFunctionRepository);
    }

    @BeforeEach
    void setUp() {
        roleFunctionRepository.deleteAll();
    }

    @Test
    @DisplayName("Should find all role-function associations by example")
    void testFindAll() {
        // Arrange
        Role role = new Role();
        role.setName("ROLE_ADMIN");
        entityManager.persist(role);

        Function function = new Function();
        function.setName("CREATE_USER");
        entityManager.persist(function);

        RoleFunction rf = new RoleFunction();
        rf.setRole(role);
        rf.setFunction(function);
        roleFunctionRepository.save(rf);

        RoleFunction probe = new RoleFunction();
        probe.setRole(role);
        Example<RoleFunction> example = Example.of(probe);

        // Act
        List<RoleFunction> result = roleFunctionDataAccess.findAll(example);

        // Assert
        assertEquals(1, result.size());
        assertEquals("CREATE_USER", result.get(0).getFunction().getName());
    }

    @Test
    @DisplayName("Should delete all role-function associations")
    void testDeleteAll() {
        // Arrange
        Role role = new Role();
        role.setName("ROLE_ADMIN");
        entityManager.persist(role);

        Function function = new Function();
        function.setName("CREATE_USER");
        entityManager.persist(function);

        RoleFunction rf = new RoleFunction();
        rf.setRole(role);
        rf.setFunction(function);
        roleFunctionRepository.save(rf);

        List<RoleFunction> toDelete = List.of(rf);

        // Act
        roleFunctionDataAccess.deleteAll(toDelete);

        // Assert
        assertEquals(0, roleFunctionRepository.count());
    }

    @Test
    @DisplayName("Should delete role-function associations by function and role")
    void testDeleteByFunctionAndRole() {
        // Arrange
        Role role1 = new Role();
        role1.setName("ROLE_ADMIN");
        entityManager.persist(role1);

        Function function1 = new Function();
        function1.setName("CREATE_USER");
        entityManager.persist(function1);

        RoleFunction rf1 = new RoleFunction();
        rf1.setRole(role1);
        rf1.setFunction(function1);
        roleFunctionRepository.save(rf1);

        List<Function> functions = List.of(function1);
        List<Role> roles = List.of(role1);

        // Act
        roleFunctionDataAccess.deleteByFunctionAndRole(functions, roles);

        // Assert
        assertEquals(0, roleFunctionRepository.count());
    }

    @Test
    @DisplayName("Should save all role-function associations")
    void testSaveAll() {
        // Arrange
        Role role = new Role();
        role.setName("ROLE_ADMIN");
        entityManager.persist(role);

        Function function1 = new Function();
        function1.setName("CREATE_USER");
        entityManager.persist(function1);

        Function function2 = new Function();
        function2.setName("DELETE_USER");
        entityManager.persist(function2);

        RoleFunction rf1 = new RoleFunction();
        rf1.setRole(role);
        rf1.setFunction(function1);

        RoleFunction rf2 = new RoleFunction();
        rf2.setRole(role);
        rf2.setFunction(function2);

        List<RoleFunction> toSave = List.of(rf1, rf2);

        // Act
        List<RoleFunction> result = roleFunctionDataAccess.saveAll(toSave);

        // Assert
        assertEquals(2, result.size());
        assertEquals(2, roleFunctionRepository.count());
    }

    @Test
    @DisplayName("Should return empty list when no matching role-function associations found")
    void testFindAll_NotFound() {
        // Arrange
        Role role = new Role();
        role.setName("ROLE_NONEXISTENT");
        RoleFunction probe = new RoleFunction();
        probe.setRole(role);
        Example<RoleFunction> example = Example.of(probe);

        // Act
        List<RoleFunction> result = roleFunctionDataAccess.findAll(example);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should delete role-function associations by function ID")
    void testDeleteByFunction() {
        // Arrange
        Role role = new Role();
        role.setName("ROLE_ADMIN");
        entityManager.persist(role);

        Function function = new Function();
        function.setName("CREATE_USER");
        entityManager.persist(function);

        RoleFunction rf = new RoleFunction();
        rf.setRole(role);
        rf.setFunction(function);
        roleFunctionRepository.save(rf);

        // Act
        roleFunctionDataAccess.deleteByFunction(function.getId());
        entityManager.flush();

        // Assert
        assertEquals(0, roleFunctionRepository.count());
    }

    @Test
    @DisplayName("Should delete all role-function associations by functions collection")
    void testDeleteAllByFunctionIn() {
        // Arrange
        Role role = new Role();
        role.setName("ROLE_ADMIN");
        entityManager.persist(role);

        Function function1 = new Function();
        function1.setName("CREATE_USER");
        entityManager.persist(function1);

        Function function2 = new Function();
        function2.setName("DELETE_USER");
        entityManager.persist(function2);

        Function function3 = new Function();
        function3.setName("UPDATE_USER");
        entityManager.persist(function3);

        RoleFunction rf1 = new RoleFunction();
        rf1.setRole(role);
        rf1.setFunction(function1);
        roleFunctionRepository.save(rf1);

        RoleFunction rf2 = new RoleFunction();
        rf2.setRole(role);
        rf2.setFunction(function2);
        roleFunctionRepository.save(rf2);

        RoleFunction rf3 = new RoleFunction();
        rf3.setRole(role);
        rf3.setFunction(function3);
        roleFunctionRepository.save(rf3);

        List<Function> functionsToDelete = List.of(function1, function2);

        // Act
        roleFunctionDataAccess.deleteAllByFunctionIn(functionsToDelete);
        entityManager.flush();
        entityManager.clear();

        // Assert
        assertEquals(1, roleFunctionRepository.count());
        List<RoleFunction> remaining = roleFunctionRepository.findAll();
        assertEquals("UPDATE_USER", remaining.get(0).getFunction().getName());
        assertNotNull(entityManager.find(Role.class, role.getId()), "刪除 RoleFunction 不得反向級聯刪除父實體 Role");
        assertNotNull(entityManager.find(Function.class, function1.getId()), "刪除 RoleFunction 不得反向級聯刪除父實體 Function");
        assertNotNull(entityManager.find(Function.class, function2.getId()), "刪除 RoleFunction 不得反向級聯刪除父實體 Function");
    }

    @Test
    @DisplayName("Should delete role-function associations by function IDs without cascading to Role or other Roles")
    void testDeleteByFunctionIds_ShouldPreserveParentRoleAndOtherBindings() {
        Role adminRole = new Role();
        adminRole.setName("admin");
        entityManager.persist(adminRole);

        Role customRole = new Role();
        customRole.setName("auditor");
        entityManager.persist(customRole);

        Function sharedFunc = new Function();
        sharedFunc.setName("View");
        sharedFunc.setParent("");
        entityManager.persist(sharedFunc);

        Function keptFunc = new Function();
        keptFunc.setName("Edit");
        keptFunc.setParent("");
        entityManager.persist(keptFunc);

        RoleFunction rfAdminShared = new RoleFunction();
        rfAdminShared.setRole(adminRole);
        rfAdminShared.setFunction(sharedFunc);
        roleFunctionRepository.save(rfAdminShared);

        RoleFunction rfCustomShared = new RoleFunction();
        rfCustomShared.setRole(customRole);
        rfCustomShared.setFunction(sharedFunc);
        roleFunctionRepository.save(rfCustomShared);

        RoleFunction rfAdminKept = new RoleFunction();
        rfAdminKept.setRole(adminRole);
        rfAdminKept.setFunction(keptFunc);
        roleFunctionRepository.save(rfAdminKept);

        entityManager.flush();
        entityManager.clear();

        // Act: 刪除 sharedFunc 對應的所有 RoleFunction
        roleFunctionDataAccess.deleteByFunctionIds(List.of(sharedFunc.getId()));
        entityManager.flush();
        entityManager.clear();

        // Assert: 父實體 Role 與 Function 皆必須完整保留，且 keptFunc 的綁定不受影響
        assertNotNull(entityManager.find(Role.class, adminRole.getId()));
        assertNotNull(entityManager.find(Role.class, customRole.getId()));
        assertNotNull(entityManager.find(Function.class, sharedFunc.getId()));
        assertNotNull(entityManager.find(Function.class, keptFunc.getId()));
        assertEquals(1, roleFunctionRepository.count());
    }

    @Test
    @DisplayName("Should delete role-function associations by role key without deleting shared Function or other Role bindings")
    void testDeleteByRoleKey_ShouldPreserveSharedFunctionsAndOtherRoleBindings() {
        Role adminRole = new Role();
        adminRole.setName("admin");
        entityManager.persist(adminRole);

        Role tempRole = new Role();
        tempRole.setName("temp_role");
        entityManager.persist(tempRole);

        Function sharedFunc = new Function();
        sharedFunc.setName("View");
        sharedFunc.setParent("");
        entityManager.persist(sharedFunc);

        RoleFunction rfAdmin = new RoleFunction();
        rfAdmin.setRole(adminRole);
        rfAdmin.setFunction(sharedFunc);
        roleFunctionRepository.save(rfAdmin);

        RoleFunction rfTemp = new RoleFunction();
        rfTemp.setRole(tempRole);
        rfTemp.setFunction(sharedFunc);
        roleFunctionRepository.save(rfTemp);

        entityManager.flush();
        entityManager.clear();

        // Act: 刪除 tempRole 的 RoleFunction
        roleFunctionDataAccess.deleteByRoleKey(tempRole.getId());
        entityManager.flush();
        entityManager.clear();

        // Assert: sharedFunc 與 adminRole 的綁定必須完整保留
        assertNotNull(entityManager.find(Function.class, sharedFunc.getId()));
        assertNotNull(entityManager.find(Role.class, adminRole.getId()));
        List<RoleFunction> remaining = roleFunctionRepository.findAll();
        assertEquals(1, remaining.size());
        assertEquals(adminRole.getId(), remaining.get(0).getRole().getId());
    }
}

