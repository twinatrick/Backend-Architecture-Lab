package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Dto.Vo.CompanyVo;
import com.example.BackendArchitectureLab.Dto.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Dto.Vo.CreateCompanyRequest;
import com.example.BackendArchitectureLab.Dto.Vo.Search.CompanySearchQuery;
import com.example.BackendArchitectureLab.Dto.Vo.UpdateCompanyRequest;

import java.util.List;

public interface ICompanyService {

    CompanyVo createCompany(CreateCompanyRequest request);

    List<CompanyVo> getAllCompanies();

    CompanyVo getCompanyById(String id);

    CompanyVo updateCompany(UpdateCompanyRequest request);

    void deleteCompany(String id);

    /**
     * 分頁搜尋公司
     *
     * @param query 搜尋查詢參數
     * @return 分頁結果
     */
    PageResult<CompanyVo> searchCompanies(CompanySearchQuery query);
}
