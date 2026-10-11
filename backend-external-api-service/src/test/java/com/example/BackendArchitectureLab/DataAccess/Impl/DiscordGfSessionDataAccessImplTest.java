package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.Entity.DiscordGfSession;
import com.example.BackendArchitectureLab.Repository.DiscordGfSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DiscordGfSessionDataAccessImpl 單元測試")
class DiscordGfSessionDataAccessImplTest {

    @Mock
    private DiscordGfSessionRepository repository;

    private DiscordGfSessionDataAccessImpl dataAccess;

    @BeforeEach
    void setUp() {
        dataAccess = new DiscordGfSessionDataAccessImpl(repository);
    }

    @Test
    @DisplayName("findByChannelIdAndUserId 應代理至 repository")
    void findByChannelIdAndUserId_ShouldDelegateToRepository() {
        DiscordGfSession session = new DiscordGfSession();
        when(repository.findByChannelIdAndUserId("ch-1", "user-1")).thenReturn(Optional.of(session));

        Optional<DiscordGfSession> result = dataAccess.findByChannelIdAndUserId("ch-1", "user-1");

        assertTrue(result.isPresent());
        assertEquals(session, result.get());
        verify(repository).findByChannelIdAndUserId("ch-1", "user-1");
    }

    @Test
    @DisplayName("save 應代理至 repository")
    void save_ShouldDelegateToRepository() {
        DiscordGfSession session = new DiscordGfSession();
        when(repository.save(session)).thenReturn(session);

        DiscordGfSession saved = dataAccess.save(session);

        assertEquals(session, saved);
        verify(repository).save(session);
    }

    @Test
    @DisplayName("findAll 應代理至 repository")
    void findAll_ShouldDelegateToRepository() {
        List<DiscordGfSession> list = List.of(new DiscordGfSession());
        when(repository.findAll()).thenReturn(list);

        List<DiscordGfSession> result = dataAccess.findAll();

        assertEquals(1, result.size());
        verify(repository).findAll();
    }

    @Test
    @DisplayName("findById 應代理至 repository")
    void findById_ShouldDelegateToRepository() {
        UUID id = UUID.randomUUID();
        DiscordGfSession session = new DiscordGfSession();
        when(repository.findById(id)).thenReturn(Optional.of(session));

        Optional<DiscordGfSession> result = dataAccess.findById(id);

        assertTrue(result.isPresent());
        assertEquals(session, result.get());
        verify(repository).findById(id);
    }
}
