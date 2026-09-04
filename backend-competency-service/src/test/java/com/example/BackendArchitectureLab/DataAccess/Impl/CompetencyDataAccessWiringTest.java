package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.DataAccess.ICompensationEventLogDataAccess;
import com.example.BackendArchitectureLab.DataAccess.ICompensationOutboxEventDataAccess;
import com.example.BackendArchitectureLab.DataAccess.ICompensationRestoreLogDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IExternalSyncCommandDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IProjectSkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.ISkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.ISkillLevelDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectSkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserSkillDataAccess;
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
        ProjectDataAccessImpl.class,
        ProjectSkillDataAccessImpl.class,
        SkillDataAccessImpl.class,
        SkillLevelDataAccessImpl.class,
        UserProjectDataAccessImpl.class,
        UserProjectSkillDataAccessImpl.class,
        UserSkillDataAccessImpl.class,
        CompensationEventLogDataAccessImpl.class,
        CompensationRestoreLogDataAccessImpl.class,
        CompensationOutboxEventDataAccessImpl.class,
        ExternalSyncCommandDataAccessImpl.class
})
@DisplayName("Competency DataAccess Spring Context Wiring 裝配測試")
class CompetencyDataAccessWiringTest {

    private final IProjectDataAccess projectDataAccess;
    private final IProjectSkillDataAccess projectSkillDataAccess;
    private final ISkillDataAccess skillDataAccess;
    private final ISkillLevelDataAccess skillLevelDataAccess;
    private final IUserProjectDataAccess userProjectDataAccess;
    private final IUserProjectSkillDataAccess userProjectSkillDataAccess;
    private final IUserSkillDataAccess userSkillDataAccess;
    private final ICompensationEventLogDataAccess compensationEventLogDataAccess;
    private final ICompensationRestoreLogDataAccess compensationRestoreLogDataAccess;
    private final ICompensationOutboxEventDataAccess compensationOutboxEventDataAccess;
    private final IExternalSyncCommandDataAccess externalSyncCommandDataAccess;

    @Autowired
    public CompetencyDataAccessWiringTest(
            IProjectDataAccess projectDataAccess,
            IProjectSkillDataAccess projectSkillDataAccess,
            ISkillDataAccess skillDataAccess,
            ISkillLevelDataAccess skillLevelDataAccess,
            IUserProjectDataAccess userProjectDataAccess,
            IUserProjectSkillDataAccess userProjectSkillDataAccess,
            IUserSkillDataAccess userSkillDataAccess,
            ICompensationEventLogDataAccess compensationEventLogDataAccess,
            ICompensationRestoreLogDataAccess compensationRestoreLogDataAccess,
            ICompensationOutboxEventDataAccess compensationOutboxEventDataAccess,
            IExternalSyncCommandDataAccess externalSyncCommandDataAccess) {
        this.projectDataAccess = projectDataAccess;
        this.projectSkillDataAccess = projectSkillDataAccess;
        this.skillDataAccess = skillDataAccess;
        this.skillLevelDataAccess = skillLevelDataAccess;
        this.userProjectDataAccess = userProjectDataAccess;
        this.userProjectSkillDataAccess = userProjectSkillDataAccess;
        this.userSkillDataAccess = userSkillDataAccess;
        this.compensationEventLogDataAccess = compensationEventLogDataAccess;
        this.compensationRestoreLogDataAccess = compensationRestoreLogDataAccess;
        this.compensationOutboxEventDataAccess = compensationOutboxEventDataAccess;
        this.externalSyncCommandDataAccess = externalSyncCommandDataAccess;
    }

    @Test
    @DisplayName("所有 DataAccess Bean 應由 Spring Context 正確完成建構子注入裝配")
    void allDataAccessBeansShouldBeWiredSuccessfully() {
        assertThat(projectDataAccess)
                .isNotNull()
                .isInstanceOf(ProjectDataAccessImpl.class);
        assertThat(projectSkillDataAccess)
                .isNotNull()
                .isInstanceOf(ProjectSkillDataAccessImpl.class);
        assertThat(skillDataAccess)
                .isNotNull()
                .isInstanceOf(SkillDataAccessImpl.class);
        assertThat(skillLevelDataAccess)
                .isNotNull()
                .isInstanceOf(SkillLevelDataAccessImpl.class);
        assertThat(userProjectDataAccess)
                .isNotNull()
                .isInstanceOf(UserProjectDataAccessImpl.class);
        assertThat(userProjectSkillDataAccess)
                .isNotNull()
                .isInstanceOf(UserProjectSkillDataAccessImpl.class);
        assertThat(userSkillDataAccess)
                .isNotNull()
                .isInstanceOf(UserSkillDataAccessImpl.class);
        assertThat(compensationEventLogDataAccess)
                .isNotNull()
                .isInstanceOf(CompensationEventLogDataAccessImpl.class);
        assertThat(compensationRestoreLogDataAccess)
                .isNotNull()
                .isInstanceOf(CompensationRestoreLogDataAccessImpl.class);
        assertThat(compensationOutboxEventDataAccess)
                .isNotNull()
                .isInstanceOf(CompensationOutboxEventDataAccessImpl.class);
        assertThat(externalSyncCommandDataAccess)
                .isNotNull()
                .isInstanceOf(ExternalSyncCommandDataAccessImpl.class);
    }
}
