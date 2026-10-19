package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Service.IFunctionQueryService;
import com.example.BackendArchitectureLab.Service.IRoleService;
import com.example.BackendArchitectureLab.Service.IUserService;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Vo.PermissionVo;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Vo.RoleVo;
import com.example.BackendArchitectureLab.Dto.Query.RoleSearchQuery;
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

    private RoleVo testRole;
    private UUID testId;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();
        testRole = new RoleVo();
        testRole.setId(testId);
        testRole.setName("ROLE_ADMIN");
    }

    @Test
    @DisplayName("Should create role with functions successfully")
    void testCreateRole() {
        when(roleService.addRoleWithFunctions(any(RoleVo.class))).thenReturn(testRole);

        ResponseType<RoleVo> response = roleController.createRole(testRole);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals(testRole, response.getData());
        verify(roleService).addRoleWithFunctions(testRole);
    }

    @Test
    @DisplayName("Should stream roles chunked successfully")
    void testStreamRoles() {
        ServerSentEvent<List<RoleVo>> chunkEvent = ServerSentEvent.<List<RoleVo>>builder()
                .event("chunk")
                .id("0")
                .data(List.of(testRole))
                .build();
        ServerSentEvent<List<RoleVo>> completeEvent = ServerSentEvent.<List<RoleVo>>builder()
                .event("complete")
                .data(List.of())
                .build();

        when(roleService.streamRolesChunked(anyInt())).thenReturn(Flux.just(chunkEvent, completeEvent));

        Flux<ServerSentEvent<List<RoleVo>>> stream = roleController.streamRoles(250);

        StepVerifier.create(stream)
                .expectNext(chunkEvent)
                .expectNext(completeEvent)
                .verifyComplete();
        verify(roleService).streamRolesChunked(250);
    }

    @Test
    @DisplayName("Should update role successfully")
    void testUpdateRole() {
        when(roleService.updateRoleWithFunctions(any(RoleVo.class))).thenReturn(testRole);

        ResponseType<RoleVo> response = roleController.updateRole(testId.toString(), testRole);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals(testRole, response.getData());
        verify(roleService).updateRoleWithFunctions(any(RoleVo.class));
    }

    @Test
    @DisplayName("Should delete role successfully")
    void testDeleteRole() {
        ResponseType<String> response = roleController.deleteRole(testId.toString());

        assertNotNull(response);
        assertEquals(200, response.getCode());
        verify(roleService).deleteRole(any(RoleVo.class));
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
        PageResult<RoleVo> pageResult = PageResult.<RoleVo>builder()
                .content(List.of(testRole))
                .totalElements(1L)
                .currentPage(0)
                .pageSize(10)
                .build();
        when(roleService.searchRoles(any(RoleSearchQuery.class))).thenReturn(pageResult);

        ResponseType<PageResult<RoleVo>> response = roleController.searchRoles(query);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals(pageResult, response.getData());
        verify(roleService).searchRoles(query);
    }
}
