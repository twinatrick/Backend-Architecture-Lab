package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Service.IUserJobLinkService;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Vo.Search.UserJobLinkSearchQuery;
import com.example.BackendArchitectureLab.Vo.UserJobLinkVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserJobLinkController 測試")
class UserJobLinkControllerTest {

    @Mock
    private IUserJobLinkService userJobLinkService;

    @InjectMocks
    private UserJobLinkController controller;

    @Test
    @DisplayName("searchUserJobLinks 應呼叫 Service 並回傳分頁結果")
    void testSearchUserJobLinks() {
        UserJobLinkSearchQuery query = new UserJobLinkSearchQuery();
        query.setPage(0);
        query.setSize(20);

        UserJobLinkVo vo = new UserJobLinkVo();
        vo.setId(UUID.randomUUID().toString());
        vo.setUserNotes("Interested");

        PageResult<UserJobLinkVo> pageResult = PageResult.<UserJobLinkVo>builder()
                .content(List.of(vo))
                .totalElements(1L)
                .totalPages(1)
                .currentPage(0)
                .pageSize(20)
                .build();

        when(userJobLinkService.searchUserJobLinks(query)).thenReturn(pageResult);

        ResponseType<PageResult<UserJobLinkVo>> response = controller.searchUserJobLinks(query);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals("使用者職缺連結搜尋成功", response.getMessage());
        assertEquals(pageResult, response.getData());
        verify(userJobLinkService).searchUserJobLinks(query);
    }

    @Test
    @DisplayName("addUserJobLink 應呼叫 Service 新增使用者職缺連結")
    void testAddUserJobLink() {
        UserJobLinkVo vo = new UserJobLinkVo();
        vo.setUserId(UUID.randomUUID().toString());
        when(userJobLinkService.createUserJobLink(vo)).thenReturn(vo);

        ResponseType<UserJobLinkVo> response = controller.addUserJobLink(vo);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals(vo, response.getData());
        verify(userJobLinkService).createUserJobLink(vo);
    }

    @Test
    @DisplayName("updateUserJobLink 應呼叫 Service 更新使用者職缺連結")
    void testUpdateUserJobLink() {
        UserJobLinkVo vo = new UserJobLinkVo();
        vo.setId(UUID.randomUUID().toString());
        when(userJobLinkService.updateUserJobLink(vo)).thenReturn(vo);

        ResponseType<UserJobLinkVo> response = controller.updateUserJobLink(vo);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals(vo, response.getData());
        verify(userJobLinkService).updateUserJobLink(vo);
    }

    @Test
    @DisplayName("getUserJobLinkById 應呼叫 Service 取得連結詳情")
    void testGetUserJobLinkById() {
        String id = UUID.randomUUID().toString();
        UserJobLinkVo vo = new UserJobLinkVo();
        vo.setId(id);
        when(userJobLinkService.getUserJobLinkById(id)).thenReturn(vo);

        ResponseType<UserJobLinkVo> response = controller.getUserJobLinkById(id);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals(vo, response.getData());
        verify(userJobLinkService).getUserJobLinkById(id);
    }

    @Test
    @DisplayName("getUserJobLinksByUserId 應呼叫 Service 取得使用者所有職缺連結")
    void testGetUserJobLinksByUserId() {
        String userId = UUID.randomUUID().toString();
        UserJobLinkVo vo = new UserJobLinkVo();
        when(userJobLinkService.getUserJobLinksByUserId(userId)).thenReturn(List.of(vo));

        ResponseType<List<UserJobLinkVo>> response = controller.getUserJobLinksByUserId(userId);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().size());
        verify(userJobLinkService).getUserJobLinksByUserId(userId);
    }

    @Test
    @DisplayName("getUserJobLinksByJobPostingId 應呼叫 Service 取得職缺所有使用者連結")
    void testGetUserJobLinksByJobPostingId() {
        String jobPostingId = UUID.randomUUID().toString();
        UserJobLinkVo vo = new UserJobLinkVo();
        when(userJobLinkService.getUserJobLinksByJobPostingId(jobPostingId)).thenReturn(List.of(vo));

        ResponseType<List<UserJobLinkVo>> response = controller.getUserJobLinksByJobPostingId(jobPostingId);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals(1, response.getData().size());
        verify(userJobLinkService).getUserJobLinksByJobPostingId(jobPostingId);
    }

    @Test
    @DisplayName("deleteUserJobLink 應呼叫 Service 刪除使用者職缺連結")
    void testDeleteUserJobLink() {
        String id = UUID.randomUUID().toString();

        ResponseType<String> response = controller.deleteUserJobLink(id);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        verify(userJobLinkService).deleteUserJobLink(id);
    }
}
