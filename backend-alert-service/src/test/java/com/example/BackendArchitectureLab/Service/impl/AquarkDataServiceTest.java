package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Service.impl.AquarkDataService;
import com.example.BackendArchitectureLab.DataAccess.IAquarkDataDataAccess;
import com.example.BackendArchitectureLab.Mapper.AquarkDataMapper;
import com.example.BackendArchitectureLab.Dto.Vo.AquarkUse.AverageAquark;
import com.example.BackendArchitectureLab.Dto.Vo.AquarkUse.AquarkDataRaw;
import com.example.BackendArchitectureLab.Dto.Vo.AquarkUse.CriteriaAPIFilter;
import com.example.BackendArchitectureLab.Entity.AquarkData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AquarkDataService.
 * Uses Mockito to mock DataAccess dependencies.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AquarkDataServiceTest {

    @Mock
    private IAquarkDataDataAccess aquarkDataDataAccess;

    @Mock
    private AquarkDataMapper aquarkDataMapper;

    @InjectMocks
    private AquarkDataService aquarkDataService;

    private Date baseDate;

    @BeforeEach
    void setUp() {
        Calendar cal = Calendar.getInstance();
        cal.set(2024, Calendar.JANUARY, 10, 8, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        baseDate = cal.getTime();

        when(aquarkDataMapper.toVo(any(AquarkData.class))).thenAnswer(invocation -> {
            AquarkData data = invocation.getArgument(0);
            AquarkDataRaw raw = new AquarkDataRaw();
            raw.setId(data.getId() == null ? null : data.getId().toString());
            raw.setStation_id(data.getStation_id());
            raw.setTrans_time(data.getTrans_time());
            raw.setRain_d(data.getRain_d());
            raw.setMoisture(data.getMoisture());
            raw.setTemperature(data.getTemperature());
            raw.setEcho(data.getEcho());
            raw.setWaterSpeedAquark(data.getWaterSpeedAquark());
            raw.setV1(data.getV1());
            raw.setV2(data.getV2());
            raw.setV3(data.getV3());
            raw.setV4(data.getV4());
            raw.setV5(data.getV5());
            raw.setV6(data.getV6());
            raw.setV7(data.getV7());
            raw.setPeak(data.isPeak());
            raw.setCSQ(data.getCSQ());
            return raw;
        });
        when(aquarkDataMapper.toEntity(any(AquarkDataRaw.class))).thenAnswer(invocation -> {
            AquarkDataRaw raw = invocation.getArgument(0);
            AquarkData data = new AquarkData();
            if (raw.getId() != null && !raw.getId().isBlank()) {
                data.setId(UUID.fromString(raw.getId()));
            }
            data.setStation_id(raw.getStation_id());
            data.setTrans_time(raw.getTrans_time());
            data.setRain_d(raw.getRain_d());
            data.setMoisture(raw.getMoisture());
            data.setTemperature(raw.getTemperature());
            data.setEcho(raw.getEcho());
            data.setWaterSpeedAquark(raw.getWaterSpeedAquark());
            data.setV1(raw.getV1());
            data.setV2(raw.getV2());
            data.setV3(raw.getV3());
            data.setV4(raw.getV4());
            data.setV5(raw.getV5());
            data.setV6(raw.getV6());
            data.setV7(raw.getV7());
            data.setPeak(raw.isPeak());
            data.setCSQ(raw.getCSQ());
            return data;
        });
    }

    @Test
    void testGetAquarkData_ReturnsMappedVos() {
        AquarkData data = buildAquarkData(UUID.randomUUID(), "S1", baseDate, 12f, 50f, 24f, 1f, 2f, true);
        when(aquarkDataDataAccess.findAll()).thenReturn(List.of(data));

        List<AquarkDataRaw> result = aquarkDataService.getAquarkData();

        assertEquals(1, result.size());
        assertEquals("S1", result.get(0).getStation_id());
        assertEquals(data.getId().toString(), result.get(0).getId());
        verify(aquarkDataDataAccess, times(1)).findAll();
    }

    @Test
    void testGetAquarkDataWithFilter_EmptyList() {
        AquarkData data = buildAquarkData(UUID.randomUUID(), "S1", baseDate, 10f, 30f, 20f, 1f, 2f, false);
        when(aquarkDataDataAccess.findAll()).thenReturn(List.of(data));

        List<AquarkDataRaw> result = aquarkDataService.getAquarkDataWithFilter(List.of());

        assertEquals(1, result.size());
        verify(aquarkDataDataAccess, times(1)).findAll();
        verify(aquarkDataDataAccess, never()).findByCriteria(any());
    }

    @Test
    void testGetAquarkDataWithFilter_WithFilters() {
        AquarkData data = buildAquarkData(UUID.randomUUID(), "S2", baseDate, 10f, 30f, 20f, 1f, 2f, false);
        CriteriaAPIFilter filter = new CriteriaAPIFilter();
        filter.setColumnName("station_id");
        filter.setType(0);
        filter.setEqual(true);
        filter.setString("S2");

        when(aquarkDataDataAccess.findByCriteria(List.of(filter))).thenReturn(List.of(data));

        List<AquarkDataRaw> result = aquarkDataService.getAquarkDataWithFilter(List.of(filter));

        assertEquals(1, result.size());
        assertEquals("S2", result.get(0).getStation_id());
        verify(aquarkDataDataAccess, times(1)).findByCriteria(List.of(filter));
    }

    @Test
    void testGetColumnNameList_IncludesKeyFields() {
        List<String> columns = aquarkDataService.getColumnNameList();

        assertTrue(columns.contains("id"));
        assertTrue(columns.contains("station_id"));
        assertTrue(columns.contains("CSQ"));
    }

    @Test
    void testGetAquarkData_NullStationOrTime() {
        AquarkDataRaw data = new AquarkDataRaw();

        assertNull(aquarkDataService.getAquarkData(data));
        verify(aquarkDataDataAccess, never()).findByStationIdAndTransTime(any(), any());
    }

    @Test
    void testGetAquarkData_Found() {
        AquarkData data = buildAquarkData(UUID.randomUUID(), "S1", baseDate, 10f, 20f, 30f, 1f, 2f, false);
        when(aquarkDataDataAccess.findByStationIdAndTransTime("S1", baseDate)).thenReturn(List.of(data));

        AquarkDataRaw input = aquarkDataMapper.toVo(data);
        AquarkDataRaw result = aquarkDataService.getAquarkData(input);

        assertNotNull(result);
        assertEquals("S1", result.getStation_id());
    }

    @Test
    void testGetAquarkData_NotFound() {
        AquarkDataRaw data = new AquarkDataRaw();
        data.setStation_id("S1");
        data.setTrans_time(baseDate);
        when(aquarkDataDataAccess.findByStationIdAndTransTime("S1", baseDate)).thenReturn(List.of());

        AquarkDataRaw result = aquarkDataService.getAquarkData(data);

        assertNull(result);
    }

    @Test
    void testInsertAquarkDataList() {
        List<AquarkDataRaw> list = List.of(aquarkDataMapper.toVo(buildAquarkData(UUID.randomUUID(), "S1", baseDate, 10f, 20f, 30f, 1f, 2f, false)));

        boolean result = aquarkDataService.insertAquarkData(list);

        assertTrue(result);
        verify(aquarkDataDataAccess, times(1)).saveAll(anyList());
    }

    @Test
    void testUpdateAquarkData() {
        AquarkData data = buildAquarkData(UUID.randomUUID(), "S1", baseDate, 10f, 20f, 30f, 1f, 2f, false);
        when(aquarkDataDataAccess.save(any(AquarkData.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AquarkDataRaw result = aquarkDataService.updateAquarkData(aquarkDataMapper.toVo(data));

        assertEquals(data.getStation_id(), result.getStation_id());
        verify(aquarkDataDataAccess, times(1)).save(any(AquarkData.class));
    }

    @Test
    void testInsertAquarkData_NewData() {
        AquarkData data = buildAquarkData(UUID.randomUUID(), "S1", baseDate, 10f, 20f, 30f, 1f, -2.5f, false);
        when(aquarkDataDataAccess.findByStationIdAndTransTime("S1", baseDate)).thenReturn(List.of());
        when(aquarkDataDataAccess.save(any(AquarkData.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AquarkDataRaw result = aquarkDataService.insertAquarkData(aquarkDataMapper.toVo(data));

        assertNotNull(result);
        assertEquals(2.5f, result.getWaterSpeedAquark());
        verify(aquarkDataDataAccess, times(1)).save(any(AquarkData.class));
    }

    @Test
    void testInsertAquarkData_ExistingData() {
        AquarkData existing = buildAquarkData(UUID.randomUUID(), "S1", baseDate, 5f, 10f, 15f, 1f, 1f, false);
        AquarkData incoming = buildAquarkData(UUID.randomUUID(), "S1", baseDate, 20f, 30f, 40f, 2f, -3f, true);
        incoming.setCSQ("CSQ-NEW");
        when(aquarkDataDataAccess.findByStationIdAndTransTime("S1", baseDate)).thenReturn(List.of(existing));
        when(aquarkDataDataAccess.save(any(AquarkData.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AquarkDataRaw result = aquarkDataService.insertAquarkData(aquarkDataMapper.toVo(incoming));

        ArgumentCaptor<AquarkData> captor = ArgumentCaptor.forClass(AquarkData.class);
        verify(aquarkDataDataAccess).save(captor.capture());
        AquarkData saved = captor.getValue();

        assertEquals(existing.getId(), saved.getId());
        assertEquals("CSQ-NEW", saved.getCSQ());
        assertEquals(20f, saved.getRain_d());
        assertEquals(30f, saved.getMoisture());
        assertEquals(40f, saved.getTemperature());
        assertEquals(2f, saved.getEcho());
        assertEquals(3f, saved.getWaterSpeedAquark());
        assertTrue(saved.isPeak());
        assertEquals(saved.getStation_id(), result.getStation_id());
    }

    @Test
    void testGetAverageAquark() {
        Date time1 = baseDate;
        Date time2 = addHours(baseDate, 4);
        Date time3 = addHours(baseDate, 6);

        AquarkData s1a = buildAquarkData(UUID.randomUUID(), "S1", time1, 24f, 10f, 20f, 2f, 1f, false);
        AquarkData s1b = buildAquarkData(UUID.randomUUID(), "S1", time2, 48f, 20f, 30f, 4f, 2f, false);
        AquarkData s2a = buildAquarkData(UUID.randomUUID(), "S2", time3, 24f, 30f, 40f, 6f, 3f, false);

        when(aquarkDataDataAccess.findByCriteria(any())).thenReturn(List.of(s1a, s1b, s2a));

        List<AverageAquark> result = aquarkDataService.getAverageAquark(addHours(baseDate, -1), addHours(baseDate, 10));

        assertEquals(2, result.size());

        Optional<AverageAquark> s1Result = result.stream().filter(a -> "S1".equals(a.getStation_id())).findFirst();
        Optional<AverageAquark> s2Result = result.stream().filter(a -> "S2".equals(a.getStation_id())).findFirst();

        assertTrue(s1Result.isPresent());
        assertTrue(s2Result.isPresent());

        AverageAquark s1Average = s1Result.get();
        assertEquals(2f, s1Average.getRain_d());
        assertEquals(15f, s1Average.getMoisture());
        assertEquals(25f, s1Average.getTemperature());
        assertEquals(3f, s1Average.getEcho());

        AverageAquark s2Average = s2Result.get();
        assertEquals(1f, s2Average.getRain_d());
        assertEquals(30f, s2Average.getMoisture());
        assertEquals(40f, s2Average.getTemperature());
    }

    private AquarkData buildAquarkData(UUID key, String stationId, Date transTime, float rain, float moisture,
                                       float temperature, float echo, float waterSpeed, boolean peak) {
        AquarkData data = new AquarkData();
        data.setId(key);
        data.setStation_id(stationId);
        data.setTrans_time(transTime);
        data.setRain_d(rain);
        data.setMoisture(moisture);
        data.setTemperature(temperature);
        data.setEcho(echo);
        data.setWaterSpeedAquark(waterSpeed);
        data.setPeak(peak);
        data.setV1(1f);
        data.setV2(2f);
        data.setV3(3f);
        data.setV4(4f);
        data.setV5(5f);
        data.setV6(6f);
        data.setV7(7f);
        return data;
    }

    private Date addHours(Date date, int hours) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.HOUR_OF_DAY, hours);
        return cal.getTime();
    }
}
