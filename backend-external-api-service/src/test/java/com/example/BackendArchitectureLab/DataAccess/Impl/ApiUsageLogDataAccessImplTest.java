package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.Entity.ApiUsageLog;
import com.example.BackendArchitectureLab.Repository.ApiUsageLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiUsageLogDataAccessImplTest {

    @Mock
    private ApiUsageLogRepository apiUsageLogRepository;

    private ApiUsageLogDataAccessImpl dataAccess;

    @BeforeEach
    void setUp() {
        dataAccess = new ApiUsageLogDataAccessImpl(apiUsageLogRepository);
    }

    @Test
    void save_DelegatesToRepository() {
        ApiUsageLog log = new ApiUsageLog();
        when(apiUsageLogRepository.save(log)).thenReturn(log);
        assertEquals(log, dataAccess.save(log));
    }

    @Test
    void findByServiceAndCreatedTimeBetween_DelegatesToRepository() {
        Date start = new Date(1L);
        Date end = new Date(2L);
        List<ApiUsageLog> logs = List.of(new ApiUsageLog());
        when(apiUsageLogRepository.findByServiceAndCreatedTimeBetweenOrderByCreatedTimeDesc("stt", start, end))
                .thenReturn(logs);
        assertEquals(logs, dataAccess.findByServiceAndCreatedTimeBetween("stt", start, end));
    }

    @Test
    void findByCreatedTimeBetween_DelegatesToRepository() {
        Date start = new Date(1L);
        Date end = new Date(2L);
        List<ApiUsageLog> logs = List.of(new ApiUsageLog());
        when(apiUsageLogRepository.findByCreatedTimeBetweenOrderByCreatedTimeDesc(start, end))
                .thenReturn(logs);
        assertEquals(logs, dataAccess.findByCreatedTimeBetween(start, end));
    }
}