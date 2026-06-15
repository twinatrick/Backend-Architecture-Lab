package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Service.IBloomFilterService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class BloomFilterInitializer implements ApplicationRunner {

    private static final Map<String, String> ENTITY_CACHE_MAP = Map.of(
        "User", "users",
        "Company", "companies",
        "Skill", "skills",
        "Role", "roles",
        "Function", "functions",
        "JobPosting", "jobPostings"
    );

    private final IBloomFilterService bloomFilterService;
    private final EntityManagerFactory entityManagerFactory;

    public BloomFilterInitializer(IBloomFilterService bloomFilterService, EntityManagerFactory entityManagerFactory) {
        this.bloomFilterService = bloomFilterService;
        this.entityManagerFactory = entityManagerFactory;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("開始初始化布隆過濾器...");

        ENTITY_CACHE_MAP.forEach((entityName, cacheName) -> {
            EntityManager em = entityManagerFactory.createEntityManager();
            try {
                String ql = "SELECT e.id FROM " + entityName + " e";
                List<?> ids = em.createQuery(ql).getResultList();
                if (ids == null || ids.isEmpty()) {
                    log.warn("布隆過濾器 [bloom:{}] 無資料可填充", cacheName);
                    return;
                }
                List<String> idStrings = ids.stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
                bloomFilterService.addAll(cacheName, idStrings);
                log.info("布隆過濾器 [bloom:{}] 已填充 {} 筆資料", cacheName, idStrings.size());
            } catch (IllegalArgumentException e) {
                log.info("Entity [{}] 不存在於此服務，跳過 BloomFilter [{}]", entityName, cacheName);
            } finally {
                em.close();
            }
        });

        log.info("布隆過濾器初始化完成");
    }
}
