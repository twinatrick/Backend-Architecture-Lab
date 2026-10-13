package com.example.BackendArchitectureLab.Mapper;

import com.example.BackendArchitectureLab.Entity.Function;
import com.example.BackendArchitectureLab.Entity.Role;
import com.example.BackendArchitectureLab.Entity.RoleFunction;
import com.example.BackendArchitectureLab.Vo.RoleOutVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleMapperTest {

    private RoleMapper roleMapper;

    @BeforeEach
    void setUp() {
        roleMapper = Mappers.getMapper(RoleMapper.class);
    }

    @Test
    @DisplayName("toSearchVo should populate functionCount and leave functionIds empty")
    void testToSearchVo_PopulatesCountAndLeavesIdsEmpty() {
        // Arrange
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setName("ROLE_MANAGER");
        role.setDescription("Manager Role");

        Function func1 = new Function();
        func1.setId(UUID.randomUUID());
        func1.setName("UserView");

        Function func2 = new Function();
        func2.setId(UUID.randomUUID());
        func2.setName("UserEdit");

        RoleFunction rf1 = new RoleFunction();
        rf1.setRole(role);
        rf1.setFunction(func1);

        RoleFunction rf2 = new RoleFunction();
        rf2.setRole(role);
        rf2.setFunction(func2);

        List<RoleFunction> roleFunctions = new ArrayList<>();
        roleFunctions.add(rf1);
        roleFunctions.add(rf2);
        role.setRoleFunctions(roleFunctions);

        // Act
        RoleOutVo searchVo = roleMapper.toSearchVo(role);

        // Assert
        assertNotNull(searchVo);
        assertEquals(role.getId(), searchVo.getId());
        assertEquals("ROLE_MANAGER", searchVo.getName());
        assertEquals(2, searchVo.getFunctionCount());
        assertNotNull(searchVo.getFunctionIds());
        assertTrue(searchVo.getFunctionIds().isEmpty(), "toSearchVo functionIds must be empty for lightweight payload");
    }

    @Test
    @DisplayName("toVo should populate both functionIds and functionCount")
    void testToVo_PopulatesBothIdsAndCount() {
        // Arrange
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setName("ROLE_ADMIN");

        Function func1 = new Function();
        UUID funcId1 = UUID.randomUUID();
        func1.setId(funcId1);

        RoleFunction rf1 = new RoleFunction();
        rf1.setRole(role);
        rf1.setFunction(func1);

        role.setRoleFunctions(List.of(rf1));

        // Act
        RoleOutVo detailVo = roleMapper.toVo(role);

        // Assert
        assertNotNull(detailVo);
        assertEquals(1, detailVo.getFunctionCount());
        assertNotNull(detailVo.getFunctionIds());
        assertEquals(1, detailVo.getFunctionIds().size());
        assertEquals(funcId1.toString(), detailVo.getFunctionIds().get(0));
    }
}
