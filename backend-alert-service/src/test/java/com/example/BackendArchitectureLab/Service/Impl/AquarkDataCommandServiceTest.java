package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.IAquarkDataDataAccess;
import com.example.BackendArchitectureLab.Entity.AquarkData;
import com.example.BackendArchitectureLab.Mapper.AquarkDataMapper;
import com.example.BackendArchitectureLab.Vo.AquarkUse.AquarkDataRaw;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AquarkDataCommandService.
 * Uses Mockito to mock DataAccess dependencies.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AquarkDataCommandServiceTest {

    @Mock
    private IAquarkDataDataAccess aquarkDataDataAccess;

    @Mock
    private AquarkDataMapper aquarkDataMapper;

    @InjectMocks
    private AquarkDataCommandService aquarkDataCommandService;

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
    void testInsertAquarkDataList() {
        List<AquarkDataRaw> list = List.of(aquarkDataMapper.toVo(buildAquarkData(UUID.randomUUID(), "S1", baseDate, 10f, 20f, 30f, 1f, 2f, false)));

        boolean result = aquarkDataCommandService.insertAquarkData(list);

        assertTrue(result);
        verify(aquarkDataDataAccess, times(1)).saveAll(anyList());
    }

    @Test
    void testUpdateAquarkData() {
        AquarkData data = buildAquarkData(UUID.randomUUID(), "S1", baseDate, 10f, 20f, 30f, 1f, 2f, false);
        when(aquarkDataDataAccess.save(any(AquarkData.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AquarkDataRaw result = aquarkDataCommandService.updateAquarkData(aquarkDataMapper.toVo(data));

        assertEquals(data.getStation_id(), result.getStation_id());
        verify(aquarkDataDataAccess, times(1)).save(any(AquarkData.class));
    }

    @Test
    void testInsertAquarkData_NewData() {
        AquarkData data = buildAquarkData(UUID.randomUUID(), "S1", baseDate, 10f, 20f, 30f, 1f, -2.5f, false);
        when(aquarkDataDataAccess.findByStationIdAndTransTime("S1", baseDate)).thenReturn(List.of());
        when(aquarkDataDataAccess.save(any(AquarkData.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AquarkDataRaw result = aquarkDataCommandService.insertAquarkData(aquarkDataMapper.toVo(data));

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

        AquarkDataRaw result = aquarkDataCommandService.insertAquarkData(aquarkDataMapper.toVo(incoming));

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
}
