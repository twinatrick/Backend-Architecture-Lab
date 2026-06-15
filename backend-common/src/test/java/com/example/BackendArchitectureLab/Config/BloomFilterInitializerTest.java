package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Service.IBloomFilterService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BloomFilterInitializerTest {

    @Mock
    private IBloomFilterService bloomFilterService;

    @Mock
    private EntityManagerFactory entityManagerFactory;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query mockQuery;

    private BloomFilterInitializer initializer;

    private final List<String> sampleIds = List.of(
        "550e8400-e29b-41d4-a716-446655440000",
        "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
        "6ba7b811-9dad-11d1-80b4-00c04fd430c8"
    );

    @BeforeEach
    void setUp() {
        lenient().when(entityManagerFactory.createEntityManager()).thenReturn(entityManager);
        lenient().when(entityManager.createQuery(anyString())).thenReturn(mockQuery);
        initializer = new BloomFilterInitializer(bloomFilterService, entityManagerFactory);
    }

    @Test
    void run_AllEntitiesHaveData_PopulatesSixFilters() {
        when(mockQuery.getResultList()).thenReturn((List) sampleIds);

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
        when(mockQuery.getResultList()).thenReturn((List) sampleIds);

        when(entityManager.createQuery(startsWith("SELECT e.id FROM Company")))
            .thenThrow(new IllegalArgumentException("Not a managed entity"));
        when(entityManager.createQuery(startsWith("SELECT e.id FROM Role")))
            .thenThrow(new IllegalArgumentException("Not a managed entity"));
        when(entityManager.createQuery(startsWith("SELECT e.id FROM JobPosting")))
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
        when(mockQuery.getResultList()).thenReturn(List.of());

        initializer.run(null);

        verify(bloomFilterService, never()).addAll(anyString(), anyList());
    }
}
