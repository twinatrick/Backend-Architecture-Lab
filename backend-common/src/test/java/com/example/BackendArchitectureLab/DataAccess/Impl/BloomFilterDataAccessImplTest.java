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
        dataAccess = new BloomFilterDataAccessImpl(emfProvider);
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

    @Test
    void findAllEntityIds_WhenEntityNameInvalid_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> dataAccess.findAllEntityIds("User; DROP TABLE users;"));
        assertThrows(IllegalArgumentException.class, () -> dataAccess.findAllEntityIds("User-Name"));
        assertThrows(IllegalArgumentException.class, () -> dataAccess.findAllEntityIds(null));
        verify(emfProvider, never()).getIfAvailable();
    }

    @Test
    void findAllEntityIds_WhenMetamodelValidatesCanonicalName_UsesCanonicalNameInQuery() {
        jakarta.persistence.metamodel.Metamodel metamodel = mock(jakarta.persistence.metamodel.Metamodel.class);
        jakarta.persistence.metamodel.EntityType entityType = mock(jakarta.persistence.metamodel.EntityType.class);
        when(entityType.getName()).thenReturn("UserEntity");
        doReturn(java.util.Set.of(entityType)).when(metamodel).getEntities();

        when(emfProvider.getIfAvailable()).thenReturn(entityManagerFactory);
        when(entityManagerFactory.getMetamodel()).thenReturn(metamodel);
        when(entityManagerFactory.createEntityManager()).thenReturn(entityManager);
        when(entityManager.createQuery("SELECT e.id FROM UserEntity e")).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of("id-1"));

        List<String> result = dataAccess.findAllEntityIds("userentity");

        assertEquals(List.of("id-1"), result);
        verify(entityManager).createQuery("SELECT e.id FROM UserEntity e");
    }

    @Test
    void findAllEntityIds_WhenMetamodelHasEntitiesButEntityNotFound_ThrowsIllegalArgumentException() {
        jakarta.persistence.metamodel.Metamodel metamodel = mock(jakarta.persistence.metamodel.Metamodel.class);
        jakarta.persistence.metamodel.EntityType entityType = mock(jakarta.persistence.metamodel.EntityType.class);
        when(entityType.getName()).thenReturn("UserEntity");
        doReturn(java.util.Set.of(entityType)).when(metamodel).getEntities();

        when(emfProvider.getIfAvailable()).thenReturn(entityManagerFactory);
        when(entityManagerFactory.getMetamodel()).thenReturn(metamodel);

        assertThrows(IllegalArgumentException.class, () -> dataAccess.findAllEntityIds("UnknownEntity"));
        verify(entityManagerFactory, never()).createEntityManager();
    }
}