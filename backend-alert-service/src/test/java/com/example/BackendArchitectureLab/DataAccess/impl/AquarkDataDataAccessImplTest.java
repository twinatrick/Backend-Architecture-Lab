package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.Repository.AquarkDataRepository;
import com.example.BackendArchitectureLab.DataAccess.IAquarkDataDataAccess;
import com.example.BackendArchitectureLab.Dto.Vo.AquarkUse.CriteriaAPIFilter;
import com.example.BackendArchitectureLab.Entity.AquarkData;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AquarkDataDataAccessImpl.
 * Uses in-memory H2 database for testing.
 */
@DataJpaTest
@Import(AquarkDataDataAccessImpl.class)
@ActiveProfiles("test")
class AquarkDataDataAccessImplTest {

    @Autowired
    private AquarkDataRepository aquarkDataRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private IAquarkDataDataAccess aquarkDataDataAccess;

    private Date baseDate;

    @BeforeEach
    void setUp() {
        aquarkDataRepository.deleteAll();
        entityManager.flush();
        Calendar cal = Calendar.getInstance();
        cal.set(2024, Calendar.JANUARY, 10, 8, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        baseDate = cal.getTime();
    }

    @Test
    @DisplayName("Should save a single aquark data record")
    void testSave() {
        AquarkData data = buildAquarkData("S1", baseDate, 10f, false);

        AquarkData saved = aquarkDataDataAccess.save(data);

        assertNotNull(saved.getId());
        assertEquals(1, aquarkDataRepository.count());
    }

    @Test
    @DisplayName("Should save a list of aquark data records")
    void testSaveAll() {
        AquarkData data1 = buildAquarkData("S1", baseDate, 10f, false);
        AquarkData data2 = buildAquarkData("S2", addHours(baseDate, 1), 20f, true);

        List<AquarkData> saved = aquarkDataDataAccess.saveAll(List.of(data1, data2));

        assertEquals(2, saved.size());
        assertEquals(2, aquarkDataRepository.count());
    }

    @Test
    @DisplayName("Should find all aquark data records")
    void testFindAll() {
        aquarkDataRepository.save(buildAquarkData("S1", baseDate, 10f, false));
        aquarkDataRepository.save(buildAquarkData("S2", addHours(baseDate, 1), 20f, true));
        entityManager.flush();

        List<AquarkData> results = aquarkDataDataAccess.findAll();

        assertEquals(2, results.size());
    }

    @Test
    @DisplayName("Should find aquark data by station id and trans time")
    void testFindByStationIdAndTransTime() {
        Date time1 = baseDate;
        Date time2 = addHours(baseDate, 2);
        aquarkDataRepository.save(buildAquarkData("S1", time1, 10f, false));
        aquarkDataRepository.save(buildAquarkData("S1", time2, 20f, false));
        entityManager.flush();

        List<AquarkData> results = aquarkDataDataAccess.findByStationIdAndTransTime("S1", time1);

        assertEquals(1, results.size());
        assertEquals(time1, results.getFirst().getTrans_time());
    }

    @Test
    @DisplayName("Should find aquark data by string criteria")
    void testFindByCriteria_StringEqual() {
        aquarkDataRepository.save(buildAquarkData("S1", baseDate, 10f, false));
        aquarkDataRepository.save(buildAquarkData("S2", addHours(baseDate, 1), 20f, true));
        entityManager.flush();

        CriteriaAPIFilter filter = new CriteriaAPIFilter();
        filter.setColumnName("station_id");
        filter.setType(0);
        filter.setEqual(true);
        filter.setString("S1");

        List<AquarkData> results = aquarkDataDataAccess.findByCriteria(List.of(filter));

        assertEquals(1, results.size());
        assertEquals("S1", results.getFirst().getStation_id());
    }

    @Test
    @DisplayName("Should find aquark data by numeric criteria")
    void testFindByCriteria_NumberGreaterThan() {
        aquarkDataRepository.save(buildAquarkData("S1", baseDate, 10f, false));
        aquarkDataRepository.save(buildAquarkData("S2", addHours(baseDate, 1), 20f, false));
        entityManager.flush();

        CriteriaAPIFilter filter = new CriteriaAPIFilter();
        filter.setColumnName("rain_d");
        filter.setType(1);
        filter.setLarge(true);
        filter.setEqual(false);
        filter.setDoubleValue(15d);

        List<AquarkData> results = aquarkDataDataAccess.findByCriteria(List.of(filter));

        assertEquals(1, results.size());
        assertEquals(20f, results.getFirst().getRain_d());
    }

    @Test
    @DisplayName("Should find aquark data by date criteria")
    void testFindByCriteria_DateLessThanOrEqual() {
        Date time1 = baseDate;
        Date time2 = addHours(baseDate, 2);
        aquarkDataRepository.save(buildAquarkData("S1", time1, 10f, false));
        aquarkDataRepository.save(buildAquarkData("S2", time2, 20f, false));
        entityManager.flush();

        CriteriaAPIFilter filter = new CriteriaAPIFilter();
        filter.setColumnName("trans_time");
        filter.setType(2);
        filter.setSmall(true);
        filter.setEqual(true);
        filter.setDate(time1);

        List<AquarkData> results = aquarkDataDataAccess.findByCriteria(List.of(filter));

        assertEquals(1, results.size());
        assertEquals(time1, results.getFirst().getTrans_time());
    }

    @Test
    @DisplayName("Should find aquark data by boolean criteria")
    void testFindByCriteria_BooleanEqual() {
        aquarkDataRepository.save(buildAquarkData("S1", baseDate, 10f, false));
        aquarkDataRepository.save(buildAquarkData("S2", addHours(baseDate, 1), 20f, true));
        entityManager.flush();

        CriteriaAPIFilter filter = new CriteriaAPIFilter();
        filter.setColumnName("isPeak");
        filter.setType(3);
        filter.setEqual(true);
        filter.setBooleanValue(true);

        List<AquarkData> results = aquarkDataDataAccess.findByCriteria(List.of(filter));

        assertEquals(1, results.size());
        assertTrue(results.getFirst().isPeak());
    }

    private AquarkData buildAquarkData(String stationId, Date transTime, float rain, boolean peak) {
        AquarkData data = new AquarkData();
        data.setStation_id(stationId);
        data.setTrans_time(transTime);
        data.setRain_d(rain);
        data.setMoisture(10f);
        data.setTemperature(20f);
        data.setEcho(1f);
        data.setWaterSpeedAquark(2f);
        data.setPeak(peak);
        data.setV1(1f);
        data.setV2(2f);
        data.setV3(3f);
        data.setV4(4f);
        data.setV5(5f);
        data.setV6(6f);
        data.setV7(7f);
        data.setCSQ("CSQ");
        return data;
    }

    private Date addHours(Date date, int hours) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.HOUR_OF_DAY, hours);
        return cal.getTime();
    }
}
