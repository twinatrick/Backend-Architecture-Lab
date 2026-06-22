package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.Cache.CacheListWrapper;
import com.example.BackendArchitectureLab.Vo.CompanyVo;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.CreateCompanyRequest;
import com.example.BackendArchitectureLab.Vo.Search.CompanySearchQuery;
import com.example.BackendArchitectureLab.Vo.UpdateCompanyRequest;

import java.util.List;

public interface ICompanyService {

    CompanyVo createCompany(CreateCompanyRequest request);

    List<CompanyVo> getAllCompanies();
    CacheListWrapper<CompanyVo> getAllCompaniesCache();

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
