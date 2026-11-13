package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Util.CacheExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(name = "org.springframework.data.redis.serializer.RedisSerializer")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableCaching
public class CachingConfiguration {

    /**
     * 與 CacheManager 同條件註冊顯式快取執行器；
     * 未啟用快取的服務（如 Reactive Gateway）不會載入本設定，也就不會要求 CacheManager。
     */
    @Bean
    @ConditionalOnMissingBean(CacheExecutor.class)
    public CacheExecutor cacheExecutor(CacheManager cacheManager) {
        return new CacheExecutor(cacheManager);
    }
}
