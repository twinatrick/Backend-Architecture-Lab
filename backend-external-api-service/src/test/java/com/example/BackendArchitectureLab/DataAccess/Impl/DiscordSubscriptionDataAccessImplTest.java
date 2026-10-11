package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.Entity.DiscordSubscription;
import com.example.BackendArchitectureLab.Repository.DiscordSubscriptionRepository;
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
@DisplayName("DiscordSubscriptionDataAccessImpl 單元測試")
class DiscordSubscriptionDataAccessImplTest {

    @Mock
    private DiscordSubscriptionRepository repository;

    private DiscordSubscriptionDataAccessImpl dataAccess;

    @BeforeEach
    void setUp() {
        dataAccess = new DiscordSubscriptionDataAccessImpl(repository);
    }

    @Test
    @DisplayName("findByGuildIdAndBotType 應代理至 repository")
    void findByGuildIdAndBotType_ShouldDelegateToRepository() {
        DiscordSubscription sub = new DiscordSubscription("g-1", "ch-1", "diary");
        when(repository.findByGuildIdAndBotType("g-1", "diary")).thenReturn(Optional.of(sub));

        Optional<DiscordSubscription> result = dataAccess.findByGuildIdAndBotType("g-1", "diary");

        assertTrue(result.isPresent());
        assertEquals(sub, result.get());
        verify(repository).findByGuildIdAndBotType("g-1", "diary");
    }

    @Test
    @DisplayName("deleteByGuildIdAndBotType 應代理至 repository")
    void deleteByGuildIdAndBotType_ShouldDelegateToRepository() {
        dataAccess.deleteByGuildIdAndBotType("g-1", "diary");
        verify(repository).deleteByGuildIdAndBotType("g-1", "diary");
    }

    @Test
    @DisplayName("findByGuildId 應代理至 repository")
    void findByGuildId_ShouldDelegateToRepository() {
        List<DiscordSubscription> list = List.of(new DiscordSubscription("g-1", "ch-1", "diary"));
        when(repository.findByGuildId("g-1")).thenReturn(list);

        List<DiscordSubscription> result = dataAccess.findByGuildId("g-1");

        assertEquals(1, result.size());
        verify(repository).findByGuildId("g-1");
    }

    @Test
    @DisplayName("findAll 應代理至 repository")
    void findAll_ShouldDelegateToRepository() {
        List<DiscordSubscription> list = List.of(new DiscordSubscription("g-1", "ch-1", "diary"));
        when(repository.findAll()).thenReturn(list);

        List<DiscordSubscription> result = dataAccess.findAll();

        assertEquals(1, result.size());
        verify(repository).findAll();
    }

    @Test
    @DisplayName("findById 應代理至 repository")
    void findById_ShouldDelegateToRepository() {
        UUID id = UUID.randomUUID();
        DiscordSubscription sub = new DiscordSubscription("g-1", "ch-1", "diary");
        when(repository.findById(id)).thenReturn(Optional.of(sub));

        Optional<DiscordSubscription> result = dataAccess.findById(id);

        assertTrue(result.isPresent());
        assertEquals(sub, result.get());
        verify(repository).findById(id);
    }

    @Test
    @DisplayName("save 應代理至 repository")
    void save_ShouldDelegateToRepository() {
        DiscordSubscription sub = new DiscordSubscription("g-1", "ch-1", "diary");
        when(repository.save(sub)).thenReturn(sub);

        DiscordSubscription saved = dataAccess.save(sub);

        assertEquals(sub, saved);
        verify(repository).save(sub);
    }

    @Test
    @DisplayName("deleteById 應代理至 repository")
    void deleteById_ShouldDelegateToRepository() {
        UUID id = UUID.randomUUID();
        dataAccess.deleteById(id);
        verify(repository).deleteById(id);
    }

    @Test
    @DisplayName("delete 應代理至 repository")
    void delete_ShouldDelegateToRepository() {
        DiscordSubscription sub = new DiscordSubscription("g-1", "ch-1", "diary");
        dataAccess.delete(sub);
        verify(repository).delete(sub);
    }
}
