package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Service.IBloomFilterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class BloomFilterInitializer implements ApplicationRunner {

    private static final Map<String, String> REPO_CACHE_MAP = Map.of(
        "userRepository", "users",
        "companyRepository", "companies",
        "skillRepository", "skills",
        "roleRepository", "roles",
        "functionRepository", "functions",
        "jobPostingRepository", "jobPostings"
    );

    private final IBloomFilterService bloomFilterService;
    private final ApplicationContext applicationContext;

    public BloomFilterInitializer(IBloomFilterService bloomFilterService, ApplicationContext applicationContext) {
        this.bloomFilterService = bloomFilterService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("開始初始化布隆過濾器...");

        REPO_CACHE_MAP.forEach((beanName, cacheName) -> {
            if (applicationContext.containsBean(beanName)) {
                JpaRepository<?, ?> repo = (JpaRepository<?, ?>) applicationContext.getBean(beanName);
                populateFromRepository(cacheName, repo);
            } else {
                log.info("Repository [{}] 不存在於此服務，跳過 BloomFilter [{}]", beanName, cacheName);
            }
        });

        log.info("布隆過濾器初始化完成");
    }

    @SuppressWarnings("unchecked")
    private void populateFromRepository(String cacheName, JpaRepository<?, ?> repo) {
        List<?> allEntities = repo.findAll();
        if (allEntities == null || allEntities.isEmpty()) {
            log.warn("布隆過濾器 [bloom:{}] 無資料可填充", cacheName);
            return;
        }
        List<String> idStrings = allEntities.stream()
            .map(e -> {
                try {
                    Object id = e.getClass().getMethod("getId").invoke(e);
                    return id != null ? id.toString() : null;
                } catch (Exception ex) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        bloomFilterService.addAll(cacheName, idStrings);
        log.info("布隆過濾器 [bloom:{}] 已填充 {} 筆資料", cacheName, idStrings.size());
    }
}
