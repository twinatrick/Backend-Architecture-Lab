package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.DataAccess.IBloomFilterDataAccess;
import com.example.BackendArchitectureLab.Service.CacheStatsPublisher;
import com.example.BackendArchitectureLab.Service.IBloomFilterService;
import com.example.BackendArchitectureLab.Service.impl.BloomFilterService;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import jakarta.persistence.EntityManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Configuration
@ConditionalOnClass(name = "org.springframework.data.redis.serializer.RedisSerializer")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RedisConfig implements CachingConfigurer {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisConfig.class);

    @Value("${spring.data.redis.host:}")
    private String configuredHost;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${app.in-docker:false}")
    private boolean inDocker;

    @Value("${redis.cache.ttl-hours:1}")
    private long cacheTtlHours;

    @Value("${cache.ttl-jitter.max-offset:0.3}")
    private double jitterMaxOffset;

    void setJitterMaxOffset(double jitterMaxOffset) {
        this.jitterMaxOffset = jitterMaxOffset;
    }

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        String host = resolveRedisHost();

        LOGGER.info("初始化 Redis 連線 - Host: {}, Port: {}", host, port);

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(host);
        config.setPort(port);

        return new LettuceConnectionFactory(config);
    }

    @Bean
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient() {
        String host = resolveRedisHost();
        LOGGER.info("初始化 Redisson 連線池 - {}:{}", host, port);

        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setConnectionPoolSize(64)
                .setConnectionMinimumIdleSize(24)
                .setIdleConnectionTimeout(10000)
                .setConnectTimeout(10000)
                .setRetryAttempts(3)
                .setRetryInterval(1500);

        return Redisson.create(config);
    }

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
    public CachePenetrationProtectionCacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            StringRedisTemplate stringRedisTemplate,
            IBloomFilterService bloomFilterService,
            NullValueTtlProperties nullValueTtlProperties,
            RedissonClient redissonClient,
            ObjectProvider<CacheStatsPublisher> statsPublisherProvider) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer jsonSerializer =
            new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(withJitter(Duration.ofHours(cacheTtlHours)))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()
                )
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer)
            );

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

        cacheConfigs.put("users", defaultConfig.entryTtl(withJitter(Duration.ofHours(2))));
        cacheConfigs.put("alertCheckLimit", defaultConfig.entryTtl(withJitter(Duration.ofHours(cacheTtlHours))));
        cacheConfigs.put("aquarkData", defaultConfig.entryTtl(withJitter(Duration.ofHours(cacheTtlHours))));
        cacheConfigs.put("skills", defaultConfig.entryTtl(withJitter(Duration.ofHours(24))));
        cacheConfigs.put("skillLevels", defaultConfig.entryTtl(withJitter(Duration.ofHours(24))));
        cacheConfigs.put("roles", defaultConfig.entryTtl(withJitter(Duration.ofHours(6))));
        cacheConfigs.put("roleFunctions", defaultConfig.entryTtl(withJitter(Duration.ofHours(6))));
        cacheConfigs.put("functions", defaultConfig.entryTtl(withJitter(Duration.ofHours(24))));
        cacheConfigs.put("companies", defaultConfig.entryTtl(withJitter(Duration.ofHours(6))));
        cacheConfigs.put("jobPostings", defaultConfig.entryTtl(withJitter(Duration.ofHours(1))));
        cacheConfigs.put("projectSkills", defaultConfig.entryTtl(withJitter(Duration.ofMinutes(30))));
        cacheConfigs.put("projectMemberSkills", defaultConfig.entryTtl(withJitter(Duration.ofMinutes(30))));
        cacheConfigs.put("projects", defaultConfig.entryTtl(withJitter(Duration.ofMinutes(10))));
        cacheConfigs.put("currentUserSkills", defaultConfig.entryTtl(withJitter(Duration.ofMinutes(10))));
        cacheConfigs.put("userJobLinks", defaultConfig.entryTtl(withJitter(Duration.ofMinutes(10))));
        cacheConfigs.put("userRoles", defaultConfig.entryTtl(withJitter(Duration.ofMinutes(10))));
        cacheConfigs.put("aquarkDataAvg", defaultConfig.entryTtl(withJitter(Duration.ofMinutes(30))));
        cacheConfigs.put("userVoiceUploads", defaultConfig.entryTtl(withJitter(Duration.ofMinutes(10))));
        cacheConfigs.put("voiceTranslations", defaultConfig.entryTtl(withJitter(Duration.ofMinutes(10))));

        LOGGER.info("Redis 快取配置完成 - 預設 TTL: {} 小時 (含亂數偏移)", cacheTtlHours);

        RedisCacheWriter cacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory);

        CacheStatsPublisher publisher = statsPublisherProvider.getIfAvailable(() -> (cn, f) -> {});
        return new CachePenetrationProtectionCacheManager(
            cacheWriter, defaultConfig, cacheConfigs,
            stringRedisTemplate, bloomFilterService, nullValueTtlProperties,
            redissonClient, publisher
        );
    }

    @Bean
    public IBloomFilterService bloomFilterService(RedissonClient redissonClient, BloomFilterProperties bloomFilterProperties) {
        return new BloomFilterService(redissonClient, bloomFilterProperties);
    }

    @Bean
    @ConditionalOnExpression("'${spring.cache.type:redis}' == 'redis' && '${app.init.enabled:true}' == 'true'")
    public ApplicationRunner bloomFilterInitializer(IBloomFilterService bloomFilterService,
                                                    ObjectProvider<EntityManagerFactory> entityManagerFactoryProvider,
                                                    ObjectProvider<IBloomFilterDataAccess> bloomFilterDataAccessProvider,
                                                    BloomFilterProperties bloomFilterProperties) {
        if (entityManagerFactoryProvider.getIfAvailable() == null
                || bloomFilterDataAccessProvider.getIfAvailable() == null) {
            return args -> LOGGER.info("EntityManagerFactory 不可用，跳過布隆過濾器初始化");
        }
        return new BloomFilterInitializer(bloomFilterService, bloomFilterDataAccessProvider.getObject(), bloomFilterProperties);
    }

    Duration withJitter(Duration base) {
        double jitter = ThreadLocalRandom.current().nextDouble(0, jitterMaxOffset);
        return base.plus(Duration.ofMillis((long) (base.toMillis() * jitter)));
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                if (exception instanceof SerializationException se) {
                    LOGGER.warn("Redis 反序列化失敗，清除快取 [{}] key: {}", cache.getName(), key);
                    cache.evict(key);
                    return;
                }
                throw exception;
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                throw exception;
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                throw exception;
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                throw exception;
            }
        };
    }

    private String resolveRedisHost() {
        if (configuredHost != null && !configuredHost.isBlank()) {
            LOGGER.info("Redis host 來自配置: {}", configuredHost);
            return configuredHost;
        }

        String resolved = inDocker ? "redis" : "localhost";
        LOGGER.info("Redis host 根據 APP_IN_DOCKER({}) 解析為: {}", inDocker, resolved);
        return resolved;
    }
}
