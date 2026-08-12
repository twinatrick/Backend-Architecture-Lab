package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.DataAccess.IBloomFilterDataAccess;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BloomFilterDataAccessImplTest {

    @Mock
    private ObjectProvider<EntityManagerFactory> emfProvider;

    @Mock
    private EntityManagerFactory entityManagerFactory;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    private IBloomFilterDataAccess dataAccess;

    @BeforeEach
    void setUp() {
        dataAccess = new BloomFilterDataAccessImpl();
        ReflectionTestUtils.setField(dataAccess, "entityManagerFactoryProvider", emfProvider);
    }

    @Test
    void findAllEntityIds_WhenEmfAvailable_ReturnsStringIds() {
        when(emfProvider.getIfAvailable()).thenReturn(entityManagerFactory);
        when(entityManagerFactory.createEntityManager()).thenReturn(entityManager);
        when(entityManager.createQuery("SELECT e.id FROM User e")).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(1L, 2L, 3L));

        List<String> result = dataAccess.findAllEntityIds("User");

        assertEquals(List.of("1", "2", "3"), result);
        verify(entityManager).close();
    }

    @Test
    void findAllEntityIds_WhenEmfUnavailable_ThrowsIllegalStateException() {
        when(emfProvider.getIfAvailable()).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> dataAccess.findAllEntityIds("User"));
        verify(entityManagerFactory, never()).createEntityManager();
    }
}