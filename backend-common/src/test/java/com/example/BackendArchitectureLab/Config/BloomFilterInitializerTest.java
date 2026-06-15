package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Service.IBloomFilterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BloomFilterInitializerTest {

    @Mock
    private IBloomFilterService bloomFilterService;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private JpaRepository<Object, UUID> userRepo;

    @Mock
    private JpaRepository<Object, UUID> companyRepo;

    @Mock
    private JpaRepository<Object, UUID> skillRepo;

    @Mock
    private JpaRepository<Object, UUID> roleRepo;

    @Mock
    private JpaRepository<Object, UUID> functionRepo;

    @Mock
    private JpaRepository<Object, UUID> jobPostingRepo;

    private BloomFilterInitializer initializer;

    private final List<UUID> sampleIds = List.of(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
    );

    @BeforeEach
    void setUp() {
        when(applicationContext.containsBean("userRepository")).thenReturn(true);
        when(applicationContext.containsBean("companyRepository")).thenReturn(true);
        when(applicationContext.containsBean("skillRepository")).thenReturn(true);
        when(applicationContext.containsBean("roleRepository")).thenReturn(true);
        when(applicationContext.containsBean("functionRepository")).thenReturn(true);
        when(applicationContext.containsBean("jobPostingRepository")).thenReturn(true);

        when(applicationContext.getBean("userRepository")).thenReturn(userRepo);
        when(applicationContext.getBean("companyRepository")).thenReturn(companyRepo);
        when(applicationContext.getBean("skillRepository")).thenReturn(skillRepo);
        when(applicationContext.getBean("roleRepository")).thenReturn(roleRepo);
        when(applicationContext.getBean("functionRepository")).thenReturn(functionRepo);
        when(applicationContext.getBean("jobPostingRepository")).thenReturn(jobPostingRepo);

        initializer = new BloomFilterInitializer(bloomFilterService, applicationContext);
    }

    private Object createMockEntity(UUID id) {
        try {
            var entity = mock(Class.forName("java.lang.Object"));
            var idMethod = entity.getClass().getMethod("getId");
            when(idMethod.invoke(entity)).thenReturn(id.toString());
            return entity;
        } catch (Exception e) {
            return null;
        }
    }

    @Test
    void run_AllReposHaveData_PopulatesSixFilters() {
        var entities = sampleIds.stream().map(id -> {
            var e = mock(Object.class);
            try {
                when(e.getClass().getMethod("getId").invoke(e)).thenReturn(id.toString());
            } catch (Exception ex) {
            }
            return e;
        }).toList();

        when(userRepo.findAll()).thenReturn(entities);
        when(companyRepo.findAll()).thenReturn(entities);
        when(skillRepo.findAll()).thenReturn(entities);
        when(roleRepo.findAll()).thenReturn(entities);
        when(functionRepo.findAll()).thenReturn(entities);
        when(jobPostingRepo.findAll()).thenReturn(entities);

        initializer.run(null);

        verify(bloomFilterService).addAll(eq("users"), anyList());
        verify(bloomFilterService).addAll(eq("companies"), anyList());
        verify(bloomFilterService).addAll(eq("skills"), anyList());
        verify(bloomFilterService).addAll(eq("roles"), anyList());
        verify(bloomFilterService).addAll(eq("functions"), anyList());
        verify(bloomFilterService).addAll(eq("jobPostings"), anyList());
    }

    @Test
    void run_SomeReposNotPresent_SkipsGracefully() {
        when(applicationContext.containsBean("companyRepository")).thenReturn(false);
        when(applicationContext.containsBean("functionRepository")).thenReturn(false);

        var entities = sampleIds.stream().map(id -> {
            var e = mock(Object.class);
            try {
                when(e.getClass().getMethod("getId").invoke(e)).thenReturn(id.toString());
            } catch (Exception ex) {
            }
            return e;
        }).toList();

        when(userRepo.findAll()).thenReturn(entities);
        when(skillRepo.findAll()).thenReturn(entities);
        when(roleRepo.findAll()).thenReturn(entities);
        when(jobPostingRepo.findAll()).thenReturn(entities);

        initializer.run(null);

        verify(bloomFilterService).addAll(eq("users"), anyList());
        verify(bloomFilterService).addAll(eq("skills"), anyList());
        verify(bloomFilterService).addAll(eq("roles"), anyList());
        verify(bloomFilterService).addAll(eq("jobPostings"), anyList());
        verify(bloomFilterService, never()).addAll(eq("companies"), anyList());
        verify(bloomFilterService, never()).addAll(eq("functions"), anyList());
    }

    @Test
    void run_AllReposEmpty_DoesNotCallAddAll() {
        when(userRepo.findAll()).thenReturn(List.of());
        when(companyRepo.findAll()).thenReturn(List.of());
        when(skillRepo.findAll()).thenReturn(List.of());
        when(roleRepo.findAll()).thenReturn(List.of());
        when(functionRepo.findAll()).thenReturn(List.of());
        when(jobPostingRepo.findAll()).thenReturn(List.of());

        initializer.run(null);

        verify(bloomFilterService, never()).addAll(anyString(), anyList());
    }
}
