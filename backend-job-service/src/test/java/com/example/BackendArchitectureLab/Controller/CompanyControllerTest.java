package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Service.ICompanyService;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.CompanyVo;
import com.example.BackendArchitectureLab.Dto.Request.CreateCompanyRequest;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Dto.Query.CompanySearchQuery;
import com.example.BackendArchitectureLab.Dto.Request.UpdateCompanyRequest;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompanyController 測試")
class CompanyControllerTest {

    @Mock
    private ICompanyService companyService;

    @InjectMocks
    private CompanyController controller;

    @Test
    @DisplayName("addCompany 應呼叫 Service 新增公司")
    void testAddCompany() {
        CreateCompanyRequest request = new CreateCompanyRequest();
        request.setName("Test Corp");

        CompanyVo vo = new CompanyVo();
        vo.setId(UUID.randomUUID().toString());
        vo.setName("Test Corp");

        when(companyService.createCompany(request)).thenReturn(vo);

        ResponseType<CompanyVo> response = controller.addCompany(request);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals("公司新增成功", response.getMessage());
        assertEquals(vo, response.getData());
        verify(companyService).createCompany(request);
    }

    @Test
    @DisplayName("streamCompanies 應呼叫 Service 串流公司資料")
    void testStreamCompanies() {
        CompanyVo vo = new CompanyVo();
        vo.setId(UUID.randomUUID().toString());
        vo.setName("Stream Corp");

        ServerSentEvent<List<CompanyVo>> sse = ServerSentEvent.<List<CompanyVo>>builder()
                .event("chunk")
                .data(List.of(vo))
                .build();

        when(companyService.streamCompaniesChunked(250)).thenReturn(Flux.just(sse));

        Flux<ServerSentEvent<List<CompanyVo>>> result = controller.streamCompanies(250);

        assertNotNull(result);
        assertEquals(List.of(sse), result.collectList().block());
        verify(companyService).streamCompaniesChunked(250);
    }

    @Test
    @DisplayName("getCompanyById 應呼叫 Service 取得公司詳情")
    void testGetCompanyById() {
        String id = UUID.randomUUID().toString();
        CompanyVo vo = new CompanyVo();
        vo.setId(id);
        vo.setName("Detail Corp");

        when(companyService.getCompanyById(id)).thenReturn(vo);

        ResponseType<CompanyVo> response = controller.getCompanyById(id);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals("公司查詢成功", response.getMessage());
        assertEquals(vo, response.getData());
        verify(companyService).getCompanyById(id);
    }

    @Test
    @DisplayName("updateCompany 應呼叫 Service 更新公司")
    void testUpdateCompany() {
        UpdateCompanyRequest request = new UpdateCompanyRequest();
        request.setId(UUID.randomUUID());
        request.setName("Updated Corp");

        CompanyVo vo = new CompanyVo();
        vo.setId(request.getId().toString());
        vo.setName("Updated Corp");

        when(companyService.updateCompany(request)).thenReturn(vo);

        ResponseType<CompanyVo> response = controller.updateCompany(request.getId().toString(), request);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals("公司更新成功", response.getMessage());
        assertEquals(vo, response.getData());
        verify(companyService).updateCompany(request);
    }

    @Test
    @DisplayName("searchCompanies 應呼叫 Service 分頁搜尋公司")
    void testSearchCompanies() {
        CompanySearchQuery query = new CompanySearchQuery();
        query.setPage(0);
        query.setSize(10);

        CompanyVo vo = new CompanyVo();
        vo.setId(UUID.randomUUID().toString());
        vo.setName("Search Corp");

        PageResult<CompanyVo> pageResult = PageResult.<CompanyVo>builder()
                .content(List.of(vo))
                .totalElements(1L)
                .totalPages(1)
                .currentPage(0)
                .pageSize(10)
                .build();

        when(companyService.searchCompanies(query)).thenReturn(pageResult);

        ResponseType<PageResult<CompanyVo>> response = controller.searchCompanies(query);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals("公司搜尋成功", response.getMessage());
        assertEquals(pageResult, response.getData());
        verify(companyService).searchCompanies(query);
    }

    @Test
    @DisplayName("deleteCompany 應呼叫 Service 刪除公司")
    void testDeleteCompany() {
        String id = UUID.randomUUID().toString();

        ResponseType<String> response = controller.deleteCompany(id);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals("公司刪除成功", response.getData());
        verify(companyService).deleteCompany(id);
    }
}
