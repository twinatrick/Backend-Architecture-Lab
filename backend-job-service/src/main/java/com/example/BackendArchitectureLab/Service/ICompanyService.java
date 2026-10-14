package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.BatchResultVo;
import com.example.BackendArchitectureLab.Vo.Cache.CacheListWrapper;
import com.example.BackendArchitectureLab.Vo.CompanyVo;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.CreateCompanyRequest;
import com.example.BackendArchitectureLab.Vo.Search.CompanySearchQuery;
import com.example.BackendArchitectureLab.Vo.UpdateCompanyRequest;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

public interface ICompanyService {

    CompanyVo createCompany(CreateCompanyRequest request);

    List<CompanyVo> getAllCompanies();
    CacheListWrapper<CompanyVo> getAllCompaniesCache();

    CompanyVo getCompanyById(String id);

    BatchResultVo<CompanyVo> getCompaniesByIds(List<UUID> ids);

    CompanyVo updateCompany(UpdateCompanyRequest request);

    void deleteCompany(String id);

    /**
     * 分頁搜尋公司
     *
     * @param query 搜尋查詢參數
     * @return 分頁結果
     */
    PageResult<CompanyVo> searchCompanies(CompanySearchQuery query);

    /**
     * 分塊串流取得公司資料 (SSE)
     *
     * @param chunkSize 每個區塊的公司筆數 (限制 10 ~ 1000)
     * @return ServerSentEvent 串流
     */
    Flux<ServerSentEvent<List<CompanyVo>>> streamCompaniesChunked(int chunkSize);
}
