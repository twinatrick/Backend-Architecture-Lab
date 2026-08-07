package com.example.BackendArchitectureLab.DataAccess.impl;

import com.example.BackendArchitectureLab.DataAccess.IBloomFilterDataAccess;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * IBloomFilterDataAccess 實作。
 * 以 EntityManager 執行動態 JPQL 查詢 Entity id，供布隆過濾器初始化使用。
 */
@Slf4j
@Component
public class BloomFilterDataAccessImpl implements IBloomFilterDataAccess {

    @Autowired
    private ObjectProvider<EntityManagerFactory> entityManagerFactoryProvider;

    @Override
    public List<String> findAllEntityIds(String entityName) {
        EntityManagerFactory emf = entityManagerFactoryProvider.getIfAvailable();
        if (emf == null) {
            throw new IllegalStateException("EntityManagerFactory 不可用，無法查詢 Entity: " + entityName);
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
