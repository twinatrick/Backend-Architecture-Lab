package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.DataAccess.ICompanyDataAccess;
import com.example.BackendArchitectureLab.DataAccess.ICompanyWebsiteDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IJobPostingDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserJobLinkDataAccess;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import({
        CompanyDataAccessImpl.class,
        CompanyWebsiteDataAccessImpl.class,
        JobPostingDataAccessImpl.class,
        UserJobLinkDataAccessImpl.class
})
@DisplayName("Job DataAccess Spring Context Wiring 裝配測試")
class JobDataAccessWiringTest {

    private final ICompanyDataAccess companyDataAccess;
    private final ICompanyWebsiteDataAccess companyWebsiteDataAccess;
    private final IJobPostingDataAccess jobPostingDataAccess;
    private final IUserJobLinkDataAccess userJobLinkDataAccess;

    @Autowired
    public JobDataAccessWiringTest(
            ICompanyDataAccess companyDataAccess,
            ICompanyWebsiteDataAccess companyWebsiteDataAccess,
            IJobPostingDataAccess jobPostingDataAccess,
            IUserJobLinkDataAccess userJobLinkDataAccess) {
        this.companyDataAccess = companyDataAccess;
        this.companyWebsiteDataAccess = companyWebsiteDataAccess;
        this.jobPostingDataAccess = jobPostingDataAccess;
        this.userJobLinkDataAccess = userJobLinkDataAccess;
    }

    @Test
    @DisplayName("所有 DataAccess Bean 應由 Spring Context 正確完成建構子注入裝配")
    void allDataAccessBeansShouldBeWiredSuccessfully() {
        assertThat(companyDataAccess)
                .isNotNull()
                .isInstanceOf(CompanyDataAccessImpl.class);
        assertThat(companyWebsiteDataAccess)
                .isNotNull()
                .isInstanceOf(CompanyWebsiteDataAccessImpl.class);
        assertThat(jobPostingDataAccess)
                .isNotNull()
                .isInstanceOf(JobPostingDataAccessImpl.class);
        assertThat(userJobLinkDataAccess)
                .isNotNull()
                .isInstanceOf(UserJobLinkDataAccessImpl.class);
    }
}
