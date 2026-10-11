package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.Entity.BotConfig;
import com.example.BackendArchitectureLab.Repository.BotConfigRepository;
import com.example.BackendArchitectureLab.Vo.Search.BotConfigSearchQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BotConfigDataAccessImplTest {

    @Mock
    private BotConfigRepository botConfigRepository;

    private BotConfigDataAccessImpl dataAccess;

    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        dataAccess = new BotConfigDataAccessImpl(botConfigRepository);
    }

    @Test
    void findAll_DelegatesToRepository() {
        BotConfig config = new BotConfig();
        when(botConfigRepository.findAll()).thenReturn(List.of(config));
        assertEquals(List.of(config), dataAccess.findAll());
    }

    @Test
    void findAllPaged_DelegatesToRepository() {
        PageRequest pageRequest = PageRequest.of(0, 10);
        Page<BotConfig> page = new PageImpl<>(List.of(new BotConfig()));
        when(botConfigRepository.findAll(pageRequest)).thenReturn(page);

        Page<BotConfig> result = dataAccess.findAllPaged(pageRequest);

        assertEquals(page, result);
        verify(botConfigRepository).findAll(pageRequest);
    }

    @Test
    void searchBotConfigs_DelegatesToRepository() {
        BotConfigSearchQuery query = new BotConfigSearchQuery();
        query.setPage(0);
        query.setSize(10);
        query.setSortBy("id");
        query.setSortDir("asc");
        Page<BotConfig> page = new PageImpl<>(List.of(new BotConfig()));
        when(botConfigRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

        Page<BotConfig> result = dataAccess.searchBotConfigs(query);

        assertEquals(page, result);
        verify(botConfigRepository).findAll(any(Specification.class), any(PageRequest.class));
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