package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.Entity.BotConfig;
import com.example.BackendArchitectureLab.Repository.BotConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BotConfigDataAccessImplTest {

    @Mock
    private BotConfigRepository botConfigRepository;

    private BotConfigDataAccessImpl dataAccess;

    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        dataAccess = new BotConfigDataAccessImpl();
        ReflectionTestUtils.setField(dataAccess, "botConfigRepository", botConfigRepository);
    }

    @Test
    void findAll_DelegatesToRepository() {
        BotConfig config = new BotConfig();
        when(botConfigRepository.findAll()).thenReturn(List.of(config));
        assertEquals(List.of(config), dataAccess.findAll());
    }

    @Test
    void findById_DelegatesToRepository() {
        BotConfig config = new BotConfig();
        when(botConfigRepository.findById(id)).thenReturn(Optional.of(config));
        assertEquals(Optional.of(config), dataAccess.findById(id));
    }

    @Test
    void save_DelegatesToRepository() {
        BotConfig config = new BotConfig();
        when(botConfigRepository.save(config)).thenReturn(config);
        assertEquals(config, dataAccess.save(config));
    }

    @Test
    void existsById_DelegatesToRepository() {
        when(botConfigRepository.existsById(id)).thenReturn(true);
        assertTrue(dataAccess.existsById(id));
    }

    @Test
    void deleteById_DelegatesToRepository() {
        dataAccess.deleteById(id);
        verify(botConfigRepository).deleteById(id);
    }
}