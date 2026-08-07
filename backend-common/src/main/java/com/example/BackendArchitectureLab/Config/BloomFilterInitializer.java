package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.DataAccess.IBloomFilterDataAccess;
import com.example.BackendArchitectureLab.Service.IBloomFilterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.List;
import java.util.Map;

@Slf4j
public class BloomFilterInitializer implements ApplicationRunner {

    private final IBloomFilterService bloomFilterService;
    private final IBloomFilterDataAccess bloomFilterDataAccess;
    private final BloomFilterProperties bloomFilterProperties;

    public BloomFilterInitializer(IBloomFilterService bloomFilterService,
                                  IBloomFilterDataAccess bloomFilterDataAccess,
                                  BloomFilterProperties bloomFilterProperties) {
        this.bloomFilterService = bloomFilterService;
        this.bloomFilterDataAccess = bloomFilterDataAccess;
        this.bloomFilterProperties = bloomFilterProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        Map<String, String> entityCacheMap = bloomFilterProperties.getEntityCacheMap();
        if (entityCacheMap.isEmpty()) {
            log.info("未配置布隆過濾器初始化項目，跳過初始化");
            return;
        }

        log.info("開始初始化布隆過濾器，共 {} 項...", entityCacheMap.size());

        entityCacheMap.forEach((entityName, cacheName) -> {
            try {
                List<String> idStrings = bloomFilterDataAccess.findAllEntityIds(entityName);
                if (idStrings.isEmpty()) {
                    log.warn("布隆過濾器 [bloom:{}] 無資料可填充", cacheName);
                    return;
                }
                bloomFilterService.addAll(cacheName, idStrings);
                log.info("布隆過濾器 [bloom:{}] 已填充 {} 筆資料", cacheName, idStrings.size());
            } catch (IllegalArgumentException e) {
                log.info("Entity [{}] 不存在於此服務，跳過 BloomFilter [{}]", entityName, cacheName);
            } catch (IllegalStateException e) {
                log.warn("布隆過濾器初始化跳過 [{}]：{}", cacheName, e.getMessage());
            }
        });

        log.info("布隆過濾器初始化完成");
    }
}
