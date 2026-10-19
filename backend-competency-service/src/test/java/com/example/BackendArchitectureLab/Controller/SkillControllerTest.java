package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Service.ISkillService;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.CurrentUserSkillVo;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Dto.Query.SkillSearchQuery;
import com.example.BackendArchitectureLab.Vo.SkillVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SkillController 測試")
class SkillControllerTest {

    @Mock
    private ISkillService skillService;

    @InjectMocks
    private SkillController controller;

    @Test
    @DisplayName("addSkill 應呼叫 Service 新增技能")
    void testAddSkill() {
        SkillVo request = new SkillVo();
        request.setName("Java");

        SkillVo result = new SkillVo();
        result.setId(UUID.randomUUID());
        result.setName("Java");

        when(skillService.addSkill(request)).thenReturn(result);

        ResponseType<SkillVo> response = controller.addSkill(request);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals("技能新增成功", response.getMessage());
        assertEquals(result, response.getData());
        verify(skillService).addSkill(request);
    }

    @Test
    @DisplayName("streamSkills 應呼叫 Service 串流技能資料")
    void testStreamSkills() {
        ServerSentEvent<List<SkillVo>> event = ServerSentEvent.<List<SkillVo>>builder()
                .event("complete")
                .data(List.of())
                .build();
        when(skillService.streamSkillsChunked(250)).thenReturn(Flux.just(event));

        Flux<ServerSentEvent<List<SkillVo>>> flux = controller.streamSkills(250);
        List<ServerSentEvent<List<SkillVo>>> events = flux.collectList().block();

        assertNotNull(events);
        assertEquals(1, events.size());
        verify(skillService).streamSkillsChunked(250);
    }

    @Test
    @DisplayName("updateSkill 應呼叫 Service 更新技能")
    void testUpdateSkill() {
        SkillVo request = new SkillVo();
        UUID id = UUID.randomUUID();
        request.setId(id);
        request.setName("Updated");

        ResponseType<String> response = controller.updateSkill(id, request);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals("技能更新成功", response.getData());
        verify(skillService).updateSkill(request);
    }

    @Test
    @DisplayName("deleteSkill 應呼叫 Service 刪除技能")
    void testDeleteSkill() {
        UUID id = UUID.randomUUID();

        ResponseType<String> response = controller.deleteSkill(id);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals("技能刪除成功", response.getData());
        verify(skillService).deleteSkill(any(SkillVo.class));
    }

    @Test
    @DisplayName("searchSkills 應呼叫 Service 搜尋技能")
    void testSearchSkills() {
        SkillSearchQuery query = new SkillSearchQuery();
        PageResult<SkillVo> pageResult = PageResult.<SkillVo>builder()
                .content(List.of())
                .totalElements(0L)
                .build();

        when(skillService.searchSkills(query)).thenReturn(pageResult);

        ResponseType<PageResult<SkillVo>> response = controller.searchSkills(query);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals("技能查詢成功", response.getMessage());
        assertEquals(pageResult, response.getData());
        verify(skillService).searchSkills(query);
    }

    @Test
    @DisplayName("getCurrentUserSkills 應呼叫 Service 取得當前使用者技能")
    void testGetCurrentUserSkills() {
        CurrentUserSkillVo skillVo = new CurrentUserSkillVo();
        skillVo.setName("Java");
        List<CurrentUserSkillVo> list = List.of(skillVo);

        when(skillService.getCurrentUserSkills()).thenReturn(list);

        ResponseType<List<CurrentUserSkillVo>> response = controller.getCurrentUserSkills();

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals("當前使用者技能查詢成功", response.getMessage());
        assertEquals(list, response.getData());
        verify(skillService).getCurrentUserSkills();
    }

    @Test
    @DisplayName("searchCurrentUserSkills 應呼叫 Service 搜尋當前使用者技能")
    void testSearchCurrentUserSkills() {
        SkillSearchQuery query = new SkillSearchQuery();
        PageResult<CurrentUserSkillVo> pageResult = PageResult.<CurrentUserSkillVo>builder()
                .content(List.of())
                .totalElements(0L)
                .build();

        when(skillService.searchCurrentUserSkills(query)).thenReturn(pageResult);

        ResponseType<PageResult<CurrentUserSkillVo>> response = controller.searchCurrentUserSkills(query);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals("當前使用者技能查詢成功", response.getMessage());
        assertEquals(pageResult, response.getData());
        verify(skillService).searchCurrentUserSkills(query);
    }
}
