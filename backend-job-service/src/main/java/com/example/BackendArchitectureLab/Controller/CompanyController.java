package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Vo.CompanyVo;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.CreateCompanyRequest;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Vo.Search.CompanySearchQuery;
import com.example.BackendArchitectureLab.Vo.UpdateCompanyRequest;
import com.example.BackendArchitectureLab.Service.ICompanyService;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/company")
@ApiControllerTag(name = "Company", description = "公司管理相關 API")
public class CompanyController {

    @Autowired
    private ICompanyService companyService;

    @PostMapping("/add")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "新增公司", description = "建立一間新的公司。")
    public ResponseType<CompanyVo> addCompany(@Valid @RequestBody CreateCompanyRequest request) {
        return ResponseType.Success(companyService.createCompany(request), "公司新增成功");
    }

    @GetMapping("/get")
    @RequirePermission("View")
    @ApiOperationOk(summary = "取得所有公司", description = "返回所有公司列表。")
    public ResponseType<List<CompanyVo>> getAllCompanies() {
        return ResponseType.Success(companyService.getAllCompanies(), "公司列表查詢成功");
    }

    @GetMapping("/get/{id}")
    @RequirePermission("View")
    @ApiOperationOk(summary = "取得公司詳情", description = "根據 ID 取得公司資訊。")
    public ResponseType<CompanyVo> getCompanyById(@PathVariable String id) {
        return ResponseType.Success(companyService.getCompanyById(id), "公司查詢成功");
    }

    @PutMapping("/update")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "更新公司", description = "更新公司資訊。")
    public ResponseType<CompanyVo> updateCompany(@RequestBody UpdateCompanyRequest request) {
        return ResponseType.Success(companyService.updateCompany(request), "公司更新成功");
    }

    @PostMapping("/search")
    @RequirePermission("View")
    @ApiOperationBadRequest(summary = "分頁搜尋公司", description = "根據條件分頁搜尋公司。")
    public ResponseType<PageResult<CompanyVo>> searchCompanies(@RequestBody CompanySearchQuery query) {
        return ResponseType.Success(companyService.searchCompanies(query), "公司搜尋成功");
    }

    @DeleteMapping("/delete/{id}")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "刪除公司", description = "根據 ID 刪除公司。")
    public ResponseType<String> deleteCompany(@PathVariable String id) {
        companyService.deleteCompany(id);
        return ResponseType.Success("公司刪除成功");
    }
}
