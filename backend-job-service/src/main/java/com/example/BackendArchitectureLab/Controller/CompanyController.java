package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiBody;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiPath;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiQuery;
import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Vo.BatchIdsRequest;
import com.example.BackendArchitectureLab.Vo.BatchResultVo;
import com.example.BackendArchitectureLab.Vo.CompanyVo;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.CreateCompanyRequest;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Vo.Search.CompanySearchQuery;
import com.example.BackendArchitectureLab.Vo.UpdateCompanyRequest;
import com.example.BackendArchitectureLab.Service.ICompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/company")
@RequiredArgsConstructor
@ApiControllerTag(name = "Company", description = "公司管理相關 API")
public class CompanyController {

    private final ICompanyService companyService;

    @PostMapping
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "新增公司", description = "建立一間新的公司。")
    public ResponseType<CompanyVo> addCompany(
            @ApiBody(description = "建立公司請求") CreateCompanyRequest request) {
        return ResponseType.Success(companyService.createCompany(request), "公司新增成功");
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequirePermission("View")
    @ApiOperationOk(summary = "串流取得公司資料", description = "使用 Server-Sent Events (SSE) 分塊串流取得公司資料，避免一次性載入大量資料。")
    public Flux<ServerSentEvent<List<CompanyVo>>> streamCompanies(
            @ApiQuery(name = "chunkSize", defaultValue = "250", description = "串流分塊大小", example = "250") int chunkSize) {
        return companyService.streamCompaniesChunked(chunkSize);
    }

    @GetMapping("/{id}")
    @RequirePermission("View")
    @ApiOperationOk(summary = "取得公司詳情", description = "根據 ID 取得公司資訊。")
    public ResponseType<CompanyVo> getCompanyById(
            @ApiPath(description = "公司唯一識別碼", example = "4e28569a-055e-459e-acd7-d76f014fd801", format = "uuid") String id) {
        return ResponseType.Success(companyService.getCompanyById(id), "公司查詢成功");
    }

    @PutMapping("/{id}")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "更新公司", description = "更新公司資訊。")
    public ResponseType<CompanyVo> updateCompany(
            @ApiPath(description = "公司唯一識別碼", example = "4e28569a-055e-459e-acd7-d76f014fd801", format = "uuid") String id,
            @ApiBody(description = "更新公司請求") UpdateCompanyRequest request) {
        request.setId(UUID.fromString(id));
        return ResponseType.Success(companyService.updateCompany(request), "公司更新成功");
    }

    @PostMapping("/search")
    @RequirePermission("View")
    @ApiOperationBadRequest(summary = "分頁搜尋公司", description = "根據條件分頁搜尋公司。")
    public ResponseType<PageResult<CompanyVo>> searchCompanies(
            @ApiBody(description = "分頁搜尋條件") CompanySearchQuery query) {
        return ResponseType.Success(companyService.searchCompanies(query), "公司搜尋成功");
    }

    @PostMapping("/batch-query")
    @RequirePermission("View")
    @ApiOperationBadRequest(summary = "批次查詢公司", description = "依據 ID 清單批次獲取公司資料，結合細粒度快取加速。")
    public ResponseType<BatchResultVo<CompanyVo>> getCompaniesBatch(
            @ApiBody(description = "批次 ID 請求") BatchIdsRequest request) {
        return ResponseType.Success(companyService.getCompaniesByIds(request.getIds()), "批次查詢成功");
    }

    @DeleteMapping("/{id}")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "刪除公司", description = "根據 ID 刪除公司。")
    public ResponseType<String> deleteCompany(
            @ApiPath(description = "公司唯一識別碼", example = "4e28569a-055e-459e-acd7-d76f014fd801", format = "uuid") String id) {
        companyService.deleteCompany(id);
        return ResponseType.Success("公司刪除成功");
    }
}
