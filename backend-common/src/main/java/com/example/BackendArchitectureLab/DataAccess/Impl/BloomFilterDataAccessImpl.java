package com.example.BackendArchitectureLab.DataAccess.Impl;

import com.example.BackendArchitectureLab.DataAccess.IBloomFilterDataAccess;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * IBloomFilterDataAccess 實作。
 * 以 EntityManager 執行動態 JPQL 查詢 Entity id，供布隆過濾器初始化使用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BloomFilterDataAccessImpl implements IBloomFilterDataAccess {

    private final ObjectProvider<EntityManagerFactory> entityManagerFactoryProvider;

    @Override
    public List<String> findAllEntityIds(String entityName) {
        if (entityName == null || !entityName.matches("^[A-Za-z0-9_]+$")) {
            throw new IllegalArgumentException("Invalid entity name: " + entityName);
        }
        EntityManagerFactory emf = entityManagerFactoryProvider.getIfAvailable();
        if (emf == null) {
            throw new IllegalStateException("EntityManagerFactory 不可用，無法查詢 Entity: " + entityName);
        }
        if (emf.getMetamodel() != null && !emf.getMetamodel().getEntities().isEmpty()) {
            boolean entityExists = emf.getMetamodel().getEntities().stream()
                    .anyMatch(e -> e.getName().equalsIgnoreCase(entityName));
            if (!entityExists) {
                throw new IllegalArgumentException("Entity not found in JPA Metamodel: " + entityName);
            }
        }
        EntityManager em = emf.createEntityManager();
        try {
            String ql = "SELECT e.id FROM " + entityName + " e";
            List<?> ids = em.createQuery(ql).getResultList();
            return ids.stream().map(Object::toString).toList();
        } finally {
            em.close();
        }
    }
}
