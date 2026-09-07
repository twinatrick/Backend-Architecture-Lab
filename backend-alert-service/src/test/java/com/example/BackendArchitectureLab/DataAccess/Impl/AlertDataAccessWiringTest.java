package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.DataAccess.IAlertCheckLimitDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IAquarkDataDataAccess;
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
        AquarkDataDataAccessImpl.class,
        AlertCheckLimitDataAccessImpl.class
})
@DisplayName("Alert DataAccess Spring Context Wiring 裝配測試")
class AlertDataAccessWiringTest {

    private final IAquarkDataDataAccess aquarkDataDataAccess;
    private final IAlertCheckLimitDataAccess alertCheckLimitDataAccess;

    @Autowired
    public AlertDataAccessWiringTest(
            IAquarkDataDataAccess aquarkDataDataAccess,
            IAlertCheckLimitDataAccess alertCheckLimitDataAccess) {
        this.aquarkDataDataAccess = aquarkDataDataAccess;
        this.alertCheckLimitDataAccess = alertCheckLimitDataAccess;
    }

    @Test
    @DisplayName("所有 DataAccess Bean 應由 Spring Context 正確完成建構子注入裝配")
    void allDataAccessBeansShouldBeWiredSuccessfully() {
        assertThat(aquarkDataDataAccess)
                .isNotNull()
                .isInstanceOf(AquarkDataDataAccessImpl.class);
        assertThat(alertCheckLimitDataAccess)
                .isNotNull()
                .isInstanceOf(AlertCheckLimitDataAccessImpl.class);
    }
}
