package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Service.IFunctionQueryService;
import com.example.BackendArchitectureLab.Service.IRoleService;
import com.example.BackendArchitectureLab.Service.IUserService;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Vo.PermissionVo;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Vo.RoleOutVo;
import com.example.BackendArchitectureLab.Vo.Search.RoleSearchQuery;
import com.example.BackendArchitectureLab.Vo.UserVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleControllerTest {

    @Mock
    private IRoleService roleService;

    @Mock
    private IFunctionQueryService functionQueryService;

    @Mock
    private IUserService userService;

    @InjectMocks
    private RoleController roleController;

    private RoleOutVo testRole;
    private UUID testId;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();
        testRole = new RoleOutVo();
        testRole.setId(testId);
        testRole.setName("ROLE_ADMIN");
    }

    @Test
    @DisplayName("Should add role successfully")
    void testAddRole() {
        when(roleService.addRole(any(RoleOutVo.class))).thenReturn(testRole);

        ResponseType<RoleOutVo> response = roleController.addRole(testRole);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals(testRole, response.getData());
        verify(roleService).addRole(testRole);
    }

    @Test
    @DisplayName("Should add role with functions successfully")
    void testAddRoleWithFunctions() {
        when(roleService.addRoleWithFunctions(any(RoleOutVo.class))).thenReturn(testRole);

        ResponseType<RoleOutVo> response = roleController.addRoleWithFunctions(testRole);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals(testRole, response.getData());
        verify(roleService).addRoleWithFunctions(testRole);
    }

    @Test
    @DisplayName("Should stream roles chunked successfully")
    void testStreamRoles() {
        ServerSentEvent<List<RoleOutVo>> chunkEvent = ServerSentEvent.<List<RoleOutVo>>builder()
                .event("chunk")
                .id("0")
                .data(List.of(testRole))
                .build();
        ServerSentEvent<List<RoleOutVo>> completeEvent = ServerSentEvent.<List<RoleOutVo>>builder()
                .event("complete")
                .data(List.of())
                .build();

        when(roleService.streamRolesChunked(anyInt())).thenReturn(Flux.just(chunkEvent, completeEvent));

        Flux<ServerSentEvent<List<RoleOutVo>>> stream = roleController.streamRoles(250);

        StepVerifier.create(stream)
                .expectNext(chunkEvent)
                .expectNext(completeEvent)
                .verifyComplete();
        verify(roleService).streamRolesChunked(250);
    }

    @Test
    @DisplayName("Should update role successfully")
    void testUpdateRole() {
        when(roleService.updateRole(any(RoleOutVo.class))).thenReturn(testRole);

        ResponseType<RoleOutVo> response = roleController.updateRole(testRole);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals(testRole, response.getData());
        verify(roleService).updateRole(testRole);
    }

    @Test
    @DisplayName("Should update role with functions successfully")
    void testUpdateRoleWithFunctions() {
        when(roleService.updateRoleWithFunctions(any(RoleOutVo.class))).thenReturn(testRole);

        ResponseType<RoleOutVo> response = roleController.updateRoleWithFunctions(testRole);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals(testRole, response.getData());
        verify(roleService).updateRoleWithFunctions(testRole);
    }

    @Test
    @DisplayName("Should delete role successfully")
    void testDeleteRole() {
        ResponseType<RoleOutVo> response = roleController.deleteRole(testRole);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals(testRole, response.getData());
        verify(roleService).deleteRole(testRole);
    }

    @Test
    @DisplayName("Should bind functions to role")
    void testRoleBindFunction() {
        PermissionVo permissionVo = new PermissionVo();
        permissionVo.setRole(testId.toString());
        permissionVo.setFunctionList(List.of("f1"));

        when(roleService.getRoleById(testId.toString())).thenReturn(testRole);

        ResponseType<RoleOutVo> response = roleController.roleBindFunction(permissionVo);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        verify(roleService).roleBindFunction(testId.toString(), List.of("f1"));
    }

    @Test
    @DisplayName("Should bind role to function")
    void testFunctionBindRole() {
        PermissionVo permissionVo = new PermissionVo();
        permissionVo.setFunction("f1");
        permissionVo.setRoleList(List.of(testId.toString()));

        FunctionVo functionVo = new FunctionVo();
        functionVo.setId("f1");
        when(functionQueryService.getFunctionById("f1")).thenReturn(functionVo);

        ResponseType<FunctionVo> response = roleController.functionBindRole(permissionVo);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        verify(roleService).functionBindRole("f1", List.of(testId.toString()));
    }

    @Test
    @DisplayName("Should search roles successfully")
    void testSearchRoles() {
        RoleSearchQuery query = new RoleSearchQuery();
        PageResult<RoleOutVo> pageResult = PageResult.<RoleOutVo>builder()
                .content(List.of(testRole))
                .totalElements(1L)
                .currentPage(0)
                .pageSize(10)
                .build();
        when(roleService.searchRoles(any(RoleSearchQuery.class))).thenReturn(pageResult);

        ResponseType<PageResult<RoleOutVo>> response = roleController.searchRoles(query);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals(pageResult, response.getData());
        verify(roleService).searchRoles(query);
    }
}
