package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.Repository.AlertCheckLimitRepository;
import com.example.BackendArchitectureLab.DataAccess.IAlertCheckLimitDataAccess;
import com.example.BackendArchitectureLab.Entity.AlertCheckLimit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AlertCheckLimitDataAccessImpl.
 * Uses in-memory H2 database for testing.
 */
@DataJpaTest
@Import(AlertCheckLimitDataAccessImpl.class)
@ActiveProfiles("test")
class AlertCheckLimitDataAccessImplTest {

    @Autowired
    private AlertCheckLimitRepository alertCheckLimitRepository;

    @Autowired
    private IAlertCheckLimitDataAccess alertCheckLimitDataAccess;

    @BeforeEach
    void setUp() {
        alertCheckLimitRepository.deleteAll();
    }

    @Test
    @DisplayName("Should save an alert check limit")
    void testSave() {
        AlertCheckLimit limit = buildLimit("aquark_data", "rain_d", 10.5);

        AlertCheckLimit saved = alertCheckLimitDataAccess.save(limit);

        assertNotNull(saved.getId());
        assertEquals(1, alertCheckLimitRepository.count());
    }

    @Test
    @DisplayName("Should find all alert check limits")
    void testFindAll() {
        alertCheckLimitRepository.save(buildLimit("aquark_data", "rain_d", 10.5));
        alertCheckLimitRepository.save(buildLimit("aquark_data", "moisture", 5.0));

        List<AlertCheckLimit> results = alertCheckLimitDataAccess.findAll();

        assertEquals(2, results.size());
    }

    @Test
    @DisplayName("Should find limits by table and column")
    void testFindByTableNameAndColumnName() {
        alertCheckLimitRepository.save(buildLimit("aquark_data", "rain_d", 10.5));
        alertCheckLimitRepository.save(buildLimit("aquark_data", "moisture", 5.0));

        List<AlertCheckLimit> results = alertCheckLimitDataAccess
                .findByTableNameAndColumnName("aquark_data", "rain_d");

        assertEquals(1, results.size());
        assertEquals("rain_d", results.getFirst().getColumnName());
    }

    @Test
    @DisplayName("Should return empty list when no limits match")
    void testFindByTableNameAndColumnName_NotFound() {
        alertCheckLimitRepository.save(buildLimit("aquark_data", "rain_d", 10.5));

        List<AlertCheckLimit> results = alertCheckLimitDataAccess
                .findByTableNameAndColumnName("aquark_data", "temperature");

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Should delete an alert check limit")
    void testDelete() {
        AlertCheckLimit saved = alertCheckLimitRepository.save(buildLimit("aquark_data", "rain_d", 10.5));

        alertCheckLimitDataAccess.delete(saved);

        assertEquals(0, alertCheckLimitRepository.count());
    }

    private AlertCheckLimit buildLimit(String tableName, String columnName, double limitValue) {
        AlertCheckLimit limit = new AlertCheckLimit();
        limit.setTableName(tableName);
        limit.setColumnName(columnName);
        limit.setLimitValue(limitValue);
        return limit;
    }
}
