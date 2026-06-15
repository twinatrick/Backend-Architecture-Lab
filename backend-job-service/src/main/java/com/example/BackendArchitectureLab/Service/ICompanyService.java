package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Dto.Vo.CompanyVo;
import com.example.BackendArchitectureLab.Dto.Vo.CreateCompanyRequest;
import com.example.BackendArchitectureLab.Dto.Vo.UpdateCompanyRequest;

import java.util.List;

public interface ICompanyService {

    CompanyVo createCompany(CreateCompanyRequest request);

    List<CompanyVo> getAllCompanies();

    CompanyVo getCompanyById(String id);

    CompanyVo updateCompany(UpdateCompanyRequest request);

    void deleteCompany(String id);
}
