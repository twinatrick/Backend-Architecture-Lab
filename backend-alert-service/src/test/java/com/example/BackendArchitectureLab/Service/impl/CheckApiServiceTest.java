package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Dto.Vo.AquarkUse.AquarkDataRaw;
import com.example.BackendArchitectureLab.Dto.Vo.AquarkUse.RowData;
import com.example.BackendArchitectureLab.Dto.Vo.AquarkUse.aquarkApiReturnVo;
import com.example.BackendArchitectureLab.Dto.Vo.Common.AlarmMessage;
import com.example.BackendArchitectureLab.Dto.Vo.AlertCheckLimitVo;
import com.example.BackendArchitectureLab.Service.IAlarmService;
import com.example.BackendArchitectureLab.Service.IAlertCheckLimitService;
import com.example.BackendArchitectureLab.Service.IApiFetcher;
import com.example.BackendArchitectureLab.Service.IAquarkDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckApiServiceTest {

    @Mock
    private IApiFetcher apiFetcher;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private IAlarmService alarmService;

    @Mock
    private IAquarkDataService aquarkDataService;

    @Mock
    private IAlertCheckLimitService alertCheckLimitService;

    @InjectMocks
    private CheckApiService checkApiService;

    @Test
    void getApiOnlyUrl_whenSuccessful_shouldReturnResponse() throws IOException {
        String url = "https://example.com/api";
        String expectedResponse = "{\"key\":\"value\"}";
        when(apiFetcher.get(url)).thenReturn(expectedResponse);

        String result = checkApiService.getApiOnlyUrl(url);

        assertEquals(expectedResponse, result);
        verify(apiFetcher, times(1)).get(url);
    }

    @Test
    void getApiOnlyUrl_whenIOException_shouldPropagate() throws IOException {
        String url = "https://example.com/api";
        when(apiFetcher.get(url)).thenThrow(new IOException("Connection refused"));

        assertThrows(IOException.class, () -> checkApiService.getApiOnlyUrl(url));
    }

    @Test
    void checkValue_whenNoValuesExceedLimit_shouldReturnEmptyList() {
        Timestamp now = Timestamp.valueOf(LocalDateTime.of(2024, 6, 27, 10, 0, 0));
        AquarkDataRaw data = createAquarkDataRaw(1f, 2f, 3f, 4f, 5f, 6f, 7f, 20f, 50f, 5f, 1f);
        data.setStation_id("ST-001");
        data.setTrans_time(now);
        when(alertCheckLimitService.getLimit("aquark_data", "v1")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v2")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v3")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v4")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v5")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v6")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v7")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "temperature")).thenReturn(createLimitVo(30.0));
        when(alertCheckLimitService.getLimit("aquark_data", "moisture")).thenReturn(createLimitVo(60.0));
        when(alertCheckLimitService.getLimit("aquark_data", "rain_d")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "water_speed_aquark")).thenReturn(createLimitVo(5.0));
        when(alertCheckLimitService.getLimit("aquark_data", "echo")).thenReturn(createLimitVo(3.0));

        List<AlarmMessage> result = checkApiService.checkValue(data);

        assertTrue(result.isEmpty());
    }

    @Test
    void checkValue_whenAllValuesExceedLimit_shouldReturnAlarmWithAllFields() {
        Timestamp now = Timestamp.valueOf(LocalDateTime.of(2024, 6, 27, 10, 0, 0));
        AquarkDataRaw data = createAquarkDataRaw(15f, 16f, 17f, 18f, 19f, 20f, 21f, 35f, 70f, 12f, 8f);
        data.setWaterSpeedAquark(8f);
        data.setStation_id("ST-001");
        data.setTrans_time(now);
        when(alertCheckLimitService.getLimit("aquark_data", "v1")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v2")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v3")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v4")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v5")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v6")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v7")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "temperature")).thenReturn(createLimitVo(30.0));
        when(alertCheckLimitService.getLimit("aquark_data", "moisture")).thenReturn(createLimitVo(60.0));
        when(alertCheckLimitService.getLimit("aquark_data", "rain_d")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "water_speed_aquark")).thenReturn(createLimitVo(5.0));
        when(alertCheckLimitService.getLimit("aquark_data", "echo")).thenReturn(createLimitVo(3.0));

        List<AlarmMessage> result = checkApiService.checkValue(data);

        assertEquals(1, result.size());
        String message = result.get(0).getMessage();
        assertEquals("ERROR", result.get(0).getLevel());
        assertTrue(message.contains("ST-001"));
        assertTrue(message.contains("v1超出限制"));
        assertTrue(message.contains("v2超出限制"));
        assertTrue(message.contains("v3超出限制"));
        assertTrue(message.contains("v4超出限制"));
        assertTrue(message.contains("v5超出限制"));
        assertTrue(message.contains("v6超出限制"));
        assertTrue(message.contains("v7超出限制"));
        assertTrue(message.contains("temperature超出限制"));
        assertTrue(message.contains("moisture超出限制"));
        assertTrue(message.contains("rain_d超出限制"));
        assertTrue(message.contains("water_speed_aquark超出限制"));
        assertTrue(message.contains("echo超出限制"));
    }

    @Test
    void checkValue_whenSomeValuesExceedLimit_shouldOnlyIncludeExceededFields() {
        Timestamp now = Timestamp.valueOf(LocalDateTime.of(2024, 6, 27, 10, 0, 0));
        AquarkDataRaw data = createAquarkDataRaw(1f, 2f, 3f, 4f, 5f, 6f, 7f, 20f, 50f, 5f, 1f);
        data.setStation_id("ST-002");
        data.setTrans_time(now);
        when(alertCheckLimitService.getLimit("aquark_data", "v1")).thenReturn(createLimitVo(0.5));
        when(alertCheckLimitService.getLimit("aquark_data", "v2")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v3")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v4")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v5")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v6")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v7")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "temperature")).thenReturn(createLimitVo(30.0));
        when(alertCheckLimitService.getLimit("aquark_data", "moisture")).thenReturn(createLimitVo(60.0));
        when(alertCheckLimitService.getLimit("aquark_data", "rain_d")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "water_speed_aquark")).thenReturn(createLimitVo(5.0));
        when(alertCheckLimitService.getLimit("aquark_data", "echo")).thenReturn(createLimitVo(3.0));

        List<AlarmMessage> result = checkApiService.checkValue(data);

        assertEquals(1, result.size());
        String message = result.get(0).getMessage();
        assertTrue(message.contains("v1超出限制"));
        assertFalse(message.contains("v2超出限制"));
        assertFalse(message.contains("temperature超出限制"));
        assertFalse(message.contains("moisture超出限制"));
        assertFalse(message.contains("rain_d超出限制"));
    }

    @Test
    void checkValue_whenLimitIsNull_shouldThrowNullPointerException() {
        Timestamp now = Timestamp.valueOf(LocalDateTime.of(2024, 6, 27, 10, 0, 0));
        AquarkDataRaw data = createAquarkDataRaw(20f, 20f, 20f, 20f, 20f, 20f, 20f, 40f, 80f, 15f, 10f);
        data.setStation_id("ST-003");
        data.setTrans_time(now);
        when(alertCheckLimitService.getLimit("aquark_data", "v1")).thenReturn(null);
        when(alertCheckLimitService.getLimit("aquark_data", "v2")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v3")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v4")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v5")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v6")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v7")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "temperature")).thenReturn(createLimitVo(30.0));
        when(alertCheckLimitService.getLimit("aquark_data", "moisture")).thenReturn(createLimitVo(60.0));
        when(alertCheckLimitService.getLimit("aquark_data", "rain_d")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "water_speed_aquark")).thenReturn(createLimitVo(5.0));
        when(alertCheckLimitService.getLimit("aquark_data", "echo")).thenReturn(createLimitVo(3.0));

        assertThrows(NullPointerException.class, () -> checkApiService.checkValue(data));
    }

    @Test
    void getAquarkApiData_whenAllDataWithinLimits_shouldNotTriggerAlarm() throws IOException {
        String jsonResponse = "{}";
        RowData rowData = new RowData();
        rowData.setStation_id("ST-001");
        rowData.setObs_time("2024-06-27 10:00:00");
        rowData.setRain_d(5f);
        aquarkApiReturnVo returnVo = new aquarkApiReturnVo();
        returnVo.setRaw(List.of(rowData));
        AquarkDataRaw savedData = createAquarkDataRaw(1f, 2f, 3f, 4f, 5f, 6f, 7f, 25f, 55f, 5f, 1f);
        savedData.setStation_id("ST-001");
        savedData.setTrans_time(Timestamp.valueOf(LocalDateTime.of(2024, 6, 27, 10, 0, 0)));

        when(apiFetcher.get(anyString())).thenReturn(jsonResponse);
        when(objectMapper.readValue(jsonResponse, aquarkApiReturnVo.class)).thenReturn(returnVo);
        when(aquarkDataService.insertAquarkData(any(AquarkDataRaw.class))).thenReturn(savedData);
        when(alertCheckLimitService.getLimit("aquark_data", "v1")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v2")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v3")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v4")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v5")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v6")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v7")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "temperature")).thenReturn(createLimitVo(30.0));
        when(alertCheckLimitService.getLimit("aquark_data", "moisture")).thenReturn(createLimitVo(60.0));
        when(alertCheckLimitService.getLimit("aquark_data", "rain_d")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "water_speed_aquark")).thenReturn(createLimitVo(5.0));
        when(alertCheckLimitService.getLimit("aquark_data", "echo")).thenReturn(createLimitVo(3.0));

        checkApiService.getAquarkApiData();

        verify(alarmService, never()).processAlarm(any());
    }

    @Test
    void getAquarkApiData_whenDataExceedsLimit_shouldTriggerAlarm() throws IOException {
        String jsonResponse = "{}";
        RowData rowData = new RowData();
        rowData.setStation_id("ST-001");
        rowData.setObs_time("2024-06-27 10:00:00");
        rowData.setRain_d(15f);
        aquarkApiReturnVo returnVo = new aquarkApiReturnVo();
        returnVo.setRaw(List.of(rowData));
        AquarkDataRaw savedData = createAquarkDataRaw(15f, 2f, 3f, 4f, 5f, 6f, 7f, 25f, 55f, 15f, 1f);
        savedData.setStation_id("ST-001");
        savedData.setTrans_time(Timestamp.valueOf(LocalDateTime.of(2024, 6, 27, 10, 0, 0)));

        when(apiFetcher.get(anyString())).thenReturn(jsonResponse);
        when(objectMapper.readValue(jsonResponse, aquarkApiReturnVo.class)).thenReturn(returnVo);
        when(aquarkDataService.insertAquarkData(any(AquarkDataRaw.class))).thenReturn(savedData);
        when(alertCheckLimitService.getLimit("aquark_data", "v1")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v2")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v3")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v4")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v5")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v6")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "v7")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "temperature")).thenReturn(createLimitVo(30.0));
        when(alertCheckLimitService.getLimit("aquark_data", "moisture")).thenReturn(createLimitVo(60.0));
        when(alertCheckLimitService.getLimit("aquark_data", "rain_d")).thenReturn(createLimitVo(10.0));
        when(alertCheckLimitService.getLimit("aquark_data", "water_speed_aquark")).thenReturn(createLimitVo(5.0));
        when(alertCheckLimitService.getLimit("aquark_data", "echo")).thenReturn(createLimitVo(3.0));

        checkApiService.getAquarkApiData();

        verify(alarmService, times(1)).processAlarm(anyList());
    }

    @Test
    void getAquarkApiData_whenIOException_shouldPropagate() throws IOException {
        when(apiFetcher.get(anyString())).thenThrow(new IOException("API unavailable"));

        assertThrows(IOException.class, () -> checkApiService.getAquarkApiData());
    }

    @Test
    void getAquarkApiData_whenMultipleUrls_shouldFetchAll() throws IOException {
        String jsonResponse = "{}";
        RowData rowData = new RowData();
        rowData.setStation_id("ST-001");
        rowData.setObs_time("2024-06-27 10:00:00");
        rowData.setRain_d(5f);
        aquarkApiReturnVo returnVo = new aquarkApiReturnVo();
        returnVo.setRaw(List.of(rowData));
        AquarkDataRaw savedData = createAquarkDataRaw(1f, 2f, 3f, 4f, 5f, 6f, 7f, 25f, 55f, 5f, 1f);
        savedData.setStation_id("ST-001");
        savedData.setTrans_time(Timestamp.valueOf(LocalDateTime.of(2024, 6, 27, 10, 0, 0)));

        when(apiFetcher.get(anyString())).thenReturn(jsonResponse);
        when(objectMapper.readValue(jsonResponse, aquarkApiReturnVo.class)).thenReturn(returnVo);
        when(aquarkDataService.insertAquarkData(any(AquarkDataRaw.class))).thenReturn(savedData);
        when(alertCheckLimitService.getLimit(anyString(), anyString())).thenReturn(createLimitVo(100.0));

        checkApiService.getAquarkApiData();

        verify(apiFetcher, times(5)).get(anyString());
    }

    private AquarkDataRaw createAquarkDataRaw(float v1, float v2, float v3, float v4,
                                              float v5, float v6, float v7,
                                              float temperature, float moisture,
                                              float rain_d, float echo) {
        AquarkDataRaw data = new AquarkDataRaw();
        data.setId(UUID.randomUUID().toString());
        data.setV1(v1);
        data.setV2(v2);
        data.setV3(v3);
        data.setV4(v4);
        data.setV5(v5);
        data.setV6(v6);
        data.setV7(v7);
        data.setTemperature(temperature);
        data.setMoisture(moisture);
        data.setRain_d(rain_d);
        data.setEcho(echo);
        data.setWaterSpeedAquark(2f);
        return data;
    }

    private AlertCheckLimitVo createLimitVo(double limitValue) {
        return new AlertCheckLimitVo(UUID.randomUUID(), "aquark_data", "test", limitValue);
    }
}
