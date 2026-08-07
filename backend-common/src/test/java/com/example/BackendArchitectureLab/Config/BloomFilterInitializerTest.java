package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.DataAccess.IBloomFilterDataAccess;
import com.example.BackendArchitectureLab.Service.IBloomFilterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BloomFilterInitializerTest {

    @Mock
    private IBloomFilterService bloomFilterService;

    @Mock
    private IBloomFilterDataAccess bloomFilterDataAccess;

    @Mock
    private BloomFilterProperties bloomFilterProperties;

    private BloomFilterInitializer initializer;

    private final List<String> sampleIds = List.of(
        "550e8400-e29b-41d4-a716-446655440000",
        "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
        "6ba7b811-9dad-11d1-80b4-00c04fd430c8"
    );

    @BeforeEach
    void setUp() {
        lenient().when(bloomFilterDataAccess.findAllEntityIds(anyString())).thenReturn(sampleIds);
        lenient().when(bloomFilterProperties.getEntityCacheMap()).thenReturn(Map.of(
            "User", "users",
            "Company", "companies",
            "Skill", "skills",
            "Role", "roles",
            "Function", "functions",
            "JobPosting", "jobPostings"
        ));
        initializer = new BloomFilterInitializer(bloomFilterService, bloomFilterDataAccess, bloomFilterProperties);
    }

    @Test
    void run_AllEntitiesHaveData_PopulatesSixFilters() {
        initializer.run(null);

        verify(bloomFilterService).addAll(eq("users"), anyList());
        verify(bloomFilterService).addAll(eq("companies"), anyList());
        verify(bloomFilterService).addAll(eq("skills"), anyList());
        verify(bloomFilterService).addAll(eq("roles"), anyList());
        verify(bloomFilterService).addAll(eq("functions"), anyList());
        verify(bloomFilterService).addAll(eq("jobPostings"), anyList());
    }

    @Test
    void run_SomeEntitiesNotPresent_SkipsGracefully() {
        when(bloomFilterDataAccess.findAllEntityIds("Company"))
            .thenThrow(new IllegalArgumentException("Not a managed entity"));
        when(bloomFilterDataAccess.findAllEntityIds("Role"))
            .thenThrow(new IllegalArgumentException("Not a managed entity"));
        when(bloomFilterDataAccess.findAllEntityIds("JobPosting"))
            .thenThrow(new IllegalArgumentException("Not a managed entity"));

        initializer.run(null);

        verify(bloomFilterService).addAll(eq("users"), anyList());
        verify(bloomFilterService, never()).addAll(eq("companies"), anyList());
        verify(bloomFilterService).addAll(eq("skills"), anyList());
        verify(bloomFilterService, never()).addAll(eq("roles"), anyList());
        verify(bloomFilterService).addAll(eq("functions"), anyList());
        verify(bloomFilterService, never()).addAll(eq("jobPostings"), anyList());
    }

    @Test
    void run_AllEntitiesEmpty_DoesNotCallAddAll() {
        when(bloomFilterDataAccess.findAllEntityIds(anyString())).thenReturn(List.of());

        initializer.run(null);

        verify(bloomFilterService, never()).addAll(anyString(), anyList());
    }

    @Test
    void run_NoEntitiesConfigured_SkipsInitialization() {
        when(bloomFilterProperties.getEntityCacheMap()).thenReturn(Map.of());

        initializer.run(null);

        verify(bloomFilterDataAccess, never()).findAllEntityIds(anyString());
        verify(bloomFilterService, never()).addAll(anyString(), anyList());
    }
}
