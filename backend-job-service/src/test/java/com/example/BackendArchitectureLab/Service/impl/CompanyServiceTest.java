package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Service.impl.CompanyService;
import com.example.BackendArchitectureLab.DataAccess.ICompanyDataAccess;
import com.example.BackendArchitectureLab.Mapper.CompanyMapper;
import com.example.BackendArchitectureLab.Dto.Vo.CompanyVo;
import com.example.BackendArchitectureLab.Dto.Vo.CreateCompanyRequest;
import com.example.BackendArchitectureLab.Dto.Vo.UpdateCompanyRequest;
import com.example.BackendArchitectureLab.Entity.Company;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompanyServiceTest {

    @Mock
    private ICompanyDataAccess companyDataAccess;

    @Mock
    private CompanyMapper companyMapper;

    @InjectMocks
    private CompanyService companyService;

    private UUID testId;
    private Company testCompany;
    private CompanyVo testCompanyVo;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();
        testCompany = new Company();
        testCompany.setId(testId);
        testCompany.setName("Test Company");
        testCompany.setDescription("Test Description");

        testCompanyVo = new CompanyVo();
        testCompanyVo.setId(testId.toString());
        testCompanyVo.setName("Test Company");
        testCompanyVo.setDescription("Test Description");

        when(companyMapper.toVo(any(Company.class))).thenAnswer(invocation -> {
            Company company = invocation.getArgument(0);
            CompanyVo vo = new CompanyVo();
            if (company.getId() != null) {
                vo.setId(company.getId().toString());
            }
            vo.setName(company.getName());
            vo.setDescription(company.getDescription());
            return vo;
        });
    }

    @Test
    void testCreateCompany_Success() {
        CreateCompanyRequest request = new CreateCompanyRequest();
        request.setName("New Company");
        request.setDescription("New Description");
        request.setWebsites(Collections.emptyList());

        when(companyDataAccess.save(any(Company.class))).thenAnswer(invocation -> {
            Company company = invocation.getArgument(0);
            company.setId(UUID.randomUUID());
            return company;
        });

        CompanyVo result = companyService.createCompany(request);

        assertNotNull(result);
        assertEquals("New Company", result.getName());
        verify(companyDataAccess).save(any(Company.class));
    }

    @Test
    void testGetAllCompanies() {
        when(companyDataAccess.findAll()).thenReturn(List.of(testCompany));

        List<CompanyVo> result = companyService.getAllCompanies();

        assertEquals(1, result.size());
        verify(companyDataAccess).findAll();
    }

    @Test
    void testGetCompanyById_Success() {
        when(companyDataAccess.findById(testId)).thenReturn(Optional.of(testCompany));

        CompanyVo result = companyService.getCompanyById(testId.toString());

        assertNotNull(result);
        assertEquals("Test Company", result.getName());
        verify(companyDataAccess).findById(testId);
    }

    @Test
    void testGetCompanyById_NotFound_ThrowsException() {
        when(companyDataAccess.findById(testId)).thenReturn(Optional.empty());

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            companyService.getCompanyById(testId.toString());
        });

        assertEquals("Company not found", exception.getMessage());
    }

    @Test
    void testUpdateCompany_Success() {
        UpdateCompanyRequest request = new UpdateCompanyRequest();
        request.setId(testId);
        request.setName("Updated Company");

        when(companyDataAccess.findById(testId)).thenReturn(Optional.of(testCompany));
        when(companyDataAccess.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyVo result = companyService.updateCompany(request);

        assertNotNull(result);
        assertEquals("Updated Company", result.getName());
        verify(companyDataAccess).findById(testId);
        verify(companyDataAccess).save(any(Company.class));
    }

    @Test
    void testUpdateCompany_NotFound_ThrowsException() {
        UpdateCompanyRequest request = new UpdateCompanyRequest();
        request.setId(testId);

        when(companyDataAccess.findById(testId)).thenReturn(Optional.empty());

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            companyService.updateCompany(request);
        });

        assertEquals("Company not found", exception.getMessage());
        verify(companyDataAccess, never()).save(any());
    }

    @Test
    void testDeleteCompany_Success() {
        when(companyDataAccess.existsById(testId)).thenReturn(true);

        companyService.deleteCompany(testId.toString());

        verify(companyDataAccess).existsById(testId);
        verify(companyDataAccess).deleteById(testId);
    }

    @Test
    void testDeleteCompany_NotFound_ThrowsException() {
        when(companyDataAccess.existsById(testId)).thenReturn(false);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            companyService.deleteCompany(testId.toString());
        });

        assertEquals("Company not found", exception.getMessage());
        verify(companyDataAccess, never()).deleteById(any());
    }

    @Test
    void testDeleteCompany_NullId_ThrowsException() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            companyService.deleteCompany(null);
        });

        assertEquals("ID must not be null", exception.getMessage());
        verify(companyDataAccess, never()).deleteById(any());
    }
}
