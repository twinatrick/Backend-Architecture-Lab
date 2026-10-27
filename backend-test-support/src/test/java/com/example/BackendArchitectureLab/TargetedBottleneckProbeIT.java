package com.example.BackendArchitectureLab;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiPath;
import com.example.BackendArchitectureLab.Config.ApiPathArgumentResolver;
import com.example.BackendArchitectureLab.Config.BloomFilterProperties;
import com.example.BackendArchitectureLab.Config.CachePenetrationProtectionCacheManager;
import com.example.BackendArchitectureLab.Service.IBloomFilterService;
import com.example.BackendArchitectureLab.TestSupport.BaseTestcontainersIntegrationTest;
import com.example.BackendArchitectureLab.TestSupport.SharedContainers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.support.DefaultDataBinderFactory;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 系統效能檢視定向瓶頸探針整合測試 (Targeted Bottleneck Probes)
 * 基於隔離 Testcontainers (PostgreSQL 16 + Redis 7.2 + Toxiproxy) 對靜態審查識別之六大結構性瓶頸進行定量實證。
 */
@ActiveProfiles("test")
@SpringBootTest(classes = TestSupportApp.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TargetedBottleneckProbeIT extends BaseTestcontainersIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(TargetedBottleneckProbeIT.class);

    private static final Map<String, Object> PROBE_REPORT = new LinkedHashMap<>();

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private IBloomFilterService bloomFilterService;

    @Autowired
    private BloomFilterProperties bloomFilterProperties;

    @Autowired
    private CachePenetrationProtectionCacheManager cacheManager;

    /**
     * 模擬 Controller 端點方法簽名（未顯式指定 @ApiPath 的 name 屬性）
     */
    @SuppressWarnings("unused")
    public void sampleGetByIdEndpoint(@ApiPath(description = "用戶 ID") UUID id) {
        // 僅供反射提取 MethodParameter 驗證參數解析器行為
    }

    @Test
    @Order(1)
    @DisplayName("探針 1：Redis KEYS 全鍵空間阻塞掃描 + N+1 HGETALL vs SCAN + Pipeline 定量對比")
    void probe1RedisKeysBlockingScanAndSequentialHgetall() {
        int totalKeys = 5000;
        int statsKeys = 200;

        stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (int keyIndex = 0; keyIndex < totalKeys; keyIndex++) {
                byte[] rawKey = ("jobPostings::search:page:" + keyIndex).getBytes(StandardCharsets.UTF_8);
                byte[] rawVal = ("payload-" + keyIndex).getBytes(StandardCharsets.UTF_8);
                connection.stringCommands().set(rawKey, rawVal);
            }
            for (int statIndex = 0; statIndex < statsKeys; statIndex++) {
                byte[] statKey = ("cache:stats:domain_" + statIndex).getBytes(StandardCharsets.UTF_8);
                connection.hashCommands().hSet(
                        statKey,
                        "hits".getBytes(StandardCharsets.UTF_8),
                        "1024".getBytes(StandardCharsets.UTF_8)
                );
                connection.hashCommands().hSet(
                        statKey,
                        "misses".getBytes(StandardCharsets.UTF_8),
                        "64".getBytes(StandardCharsets.UTF_8)
                );
            }
            return null;
        });

        long keysStartNanos = System.nanoTime();
        Set<String> matchedStatKeys = stringRedisTemplate.keys("cache:stats:*");
        long keysScanNanos = System.nanoTime() - keysStartNanos;

        long sequentialStartNanos = System.nanoTime();
        int fetchedHashes = 0;
        if (matchedStatKeys != null) {
            for (String statKey : matchedStatKeys) {
                Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(statKey);
                if (!entries.isEmpty()) {
                    fetchedHashes++;
                }
            }
        }
        long sequentialTotalNanos = (System.nanoTime() - sequentialStartNanos) + keysScanNanos;

        long optimizedStartNanos = System.nanoTime();
        List<String> scannedStatKeys = new ArrayList<>();
        ScanOptions scanOptions = ScanOptions.scanOptions().match("cache:stats:*").count(500).build();
        try (Cursor<String> cursor = stringRedisTemplate.scan(scanOptions)) {
            while (cursor.hasNext()) {
                scannedStatKeys.add(cursor.next());
            }
        }
        List<Object> pipelinedResults = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String statKey : scannedStatKeys) {
                connection.hashCommands().hGetAll(statKey.getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });
        long optimizedTotalNanos = System.nanoTime() - optimizedStartNanos;

        double sequentialMs = sequentialTotalNanos / 1_000_000.0;
        double optimizedMs = optimizedTotalNanos / 1_000_000.0;
        double speedupRatio = sequentialMs / Math.max(optimizedMs, 0.01);

        assertEquals(statsKeys, fetchedHashes, "應完整讀取所有快取統計 Hash");
        assertEquals(statsKeys, pipelinedResults.size(), "Pipeline 應完整讀取所有快取統計 Hash");

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalKeyspaceSize", totalKeys + statsKeys);
        metrics.put("statsKeyCount", statsKeys);
        metrics.put("keysPlusSequentialHgetallMs", roundTwoDecimals(sequentialMs));
        metrics.put("scanPlusPipelineHgetallMs", roundTwoDecimals(optimizedMs));
        metrics.put("speedupFactor", roundTwoDecimals(speedupRatio));
        PROBE_REPORT.put("probe1_redisKeysAndSequentialHgetall", metrics);

        log.info("[Probe 1] KEYS + 逐鍵 HGETALL ({} 鍵): {} ms | SCAN + Pipeline: {} ms | 加速比: {}x",
                statsKeys, roundTwoDecimals(sequentialMs), roundTwoDecimals(optimizedMs), roundTwoDecimals(speedupRatio));
    }

    @Test
    @Order(2)
    @DisplayName("探針 2：CachePenetrationProtectionCache 10 許可證公平信號量排隊與非 sync 布隆攔截契約缺陷驗證")
    void probe2CacheSemaphoreContentionAndBloomInterceptContract() throws InterruptedException {
        Cache cache = cacheManager.getCache("functions");
        assertNotNull(cache, "functions 快取實例不可為空");

        String hotKey = "all";
        cache.put(hotKey, "CachedFunctionListPayload");
        // 先執行一次預熱以觸發 Kafka Producer 首次連接與 cache-stats Topic Metadata 初始化
        cache.get(hotKey, () -> "FallbackFunctionListPayload");

        int concurrentCalls = 200;
        ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch syncDoneLatch = new CountDownLatch(concurrentCalls);
        AtomicLong syncTotalLatencyNanos = new AtomicLong(0);
        AtomicLong syncMaxLatencyNanos = new AtomicLong(0);

        for (int callIndex = 0; callIndex < concurrentCalls; callIndex++) {
            virtualExecutor.submit(() -> {
                try {
                    startLatch.await();
                    long callStart = System.nanoTime();
                    String value = cache.get(hotKey, () -> "FallbackFunctionListPayload");
                    long elapsed = System.nanoTime() - callStart;
                    assertEquals("CachedFunctionListPayload", value);
                    syncTotalLatencyNanos.addAndGet(elapsed);
                    syncMaxLatencyNanos.accumulateAndGet(elapsed, Math::max);
                } catch (Exception exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    syncDoneLatch.countDown();
                }
            });
        }

        long syncWallStart = System.nanoTime();
        startLatch.countDown();
        boolean completed = syncDoneLatch.await(30, TimeUnit.SECONDS);
        long syncWallNanos = System.nanoTime() - syncWallStart;
        virtualExecutor.shutdown();
        assertTrue(completed, "200 併發 sync=true 快取讀取應於限時內完成");

        // 驗證 get(Object key) 在布隆過濾器判定不存在時回傳 null (Cache Miss) 而非 ValueWrapper 之契約行為
        if (!bloomFilterProperties.getEntities().contains("Function:functions")) {
            bloomFilterProperties.getEntities().add("Function:functions");
        }
        bloomFilterService.add("functions", hotKey);
        Cache.ValueWrapper nonSyncWrapper = cache.get("non-existent-function-key");
        assertNull(nonSyncWrapper, "非 sync 路徑 get(key) 在布隆攔截時回傳 null，依 Spring Cache 契約將被視為 Cache Miss 並穿透執行方法");

        double avgSyncHitMs = (syncTotalLatencyNanos.get() / (double) concurrentCalls) / 1_000_000.0;
        double maxSyncHitMs = syncMaxLatencyNanos.get() / 1_000_000.0;
        double wallSyncHitMs = syncWallNanos / 1_000_000.0;

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("concurrentVirtualThreads", concurrentCalls);
        metrics.put("semaphorePermits", 10);
        metrics.put("avgSyncHitLatencyMs", roundTwoDecimals(avgSyncHitMs));
        metrics.put("maxSyncHitLatencyMs", roundTwoDecimals(maxSyncHitMs));
        metrics.put("totalWallTimeMs", roundTwoDecimals(wallSyncHitMs));
        metrics.put("nonSyncBloomInterceptReturnsNullCacheMiss", true);
        PROBE_REPORT.put("probe2_cacheSemaphoreAndBloomContract", metrics);

        log.info("[Probe 2] 200 VT 命中 @Cacheable(sync=true) 10-permit Semaphore: Avg={} ms, Max={} ms, Wall={} ms | 非 sync 布隆攔截回傳 null={}",
                roundTwoDecimals(avgSyncHitMs), roundTwoDecimals(maxSyncHitMs), roundTwoDecimals(wallSyncHitMs), true);
    }

    @Test
    @Order(3)
    @DisplayName("探針 3：BloomFilter 預設容量 (10,000 -> 143,775 bits) 在大表 (150,000 筆) 下位元飽和導致 99.9%+ 假陽性穿透")
    void probe3BloomFilterBitSaturationAndCapacityComparison() {
        String saturatedDomain = "probeSaturatedDomain";
        String sizedDomain = "probeProperlySizedDomain";

        if (!bloomFilterProperties.getEntities().contains("ProbeSaturatedEntity:" + saturatedDomain)) {
            bloomFilterProperties.getEntities().add("ProbeSaturatedEntity:" + saturatedDomain);
        }
        if (!bloomFilterProperties.getEntities().contains("ProbeSizedEntity:" + sizedDomain)) {
            bloomFilterProperties.getEntities().add("ProbeSizedEntity:" + sizedDomain);
        }

        bloomFilterService.clear(saturatedDomain);
        bloomFilterService.clear(sizedDomain);
        redissonClient.getBloomFilter("bloom:" + saturatedDomain).delete();
        redissonClient.getBloomFilter("bloom:" + sizedDomain).delete();

        bloomFilterProperties.setDefaultExpectedInsertions(10_000L);
        bloomFilterProperties.setDefaultFalseProbability(0.001);

        List<String> largeBatch = new ArrayList<>(150_000);
        for (int itemIndex = 0; itemIndex < 150_000; itemIndex++) {
            largeBatch.add(String.format("00000000-0000-0000-0006-%012d", itemIndex));
        }

        // 1. 生產預設容量 (10,000 -> 143,775 bits) 塞入 150,000 筆 (對應 user_skill 150,010 / user_project 200,005 筆)
        long addStartNanos = System.nanoTime();
        bloomFilterService.addAll(saturatedDomain, largeBatch);
        double singleBatchAddMs = (System.nanoTime() - addStartNanos) / 1_000_000.0;

        RBloomFilter<String> saturatedRawFilter = redissonClient.getBloomFilter("bloom:" + saturatedDomain);
        long saturatedBitSize = saturatedRawFilter.getSize();

        int attackSamples = 1_000;
        int saturatedFalsePositives = 0;
        for (int sampleIdx = 0; sampleIdx < attackSamples; sampleIdx++) {
            String nonExistentKey = String.format("99999999-9999-9999-9999-%012d", sampleIdx);
            if (bloomFilterService.mightContain(saturatedDomain, nonExistentKey)) {
                saturatedFalsePositives++;
            }
        }
        double saturatedFprPct = (saturatedFalsePositives * 100.0) / attackSamples;

        // 2. 對照組：擴容為 expectedInsertions = 500,000 (7,188,794 bits)
        BloomFilterProperties.BloomFilterConfig properConfig = new BloomFilterProperties.BloomFilterConfig();
        properConfig.setExpectedInsertions(500_000L);
        properConfig.setFalseProbability(0.001);
        bloomFilterProperties.getOverrides().put(sizedDomain, properConfig);

        bloomFilterService.addAll(sizedDomain, largeBatch);
        RBloomFilter<String> sizedRawFilter = redissonClient.getBloomFilter("bloom:" + sizedDomain);
        long sizedBitSize = sizedRawFilter.getSize();

        int sizedFalsePositives = 0;
        for (int sampleIdx = 0; sampleIdx < attackSamples; sampleIdx++) {
            String nonExistentKey = String.format("99999999-9999-9999-9999-%012d", sampleIdx);
            if (bloomFilterService.mightContain(sizedDomain, nonExistentKey)) {
                sizedFalsePositives++;
            }
        }
        double sizedFprPct = (sizedFalsePositives * 100.0) / attackSamples;

        bloomFilterService.clear(saturatedDomain);
        bloomFilterService.clear(sizedDomain);
        redissonClient.getBloomFilter("bloom:" + saturatedDomain).delete();
        redissonClient.getBloomFilter("bloom:" + sizedDomain).delete();

        assertTrue(saturatedFprPct > 95.0, "預設 10,000 容量塞入 150,000 筆後位元飽和，假陽性穿透率超過 95% (防穿透形同虛設)");
        assertTrue(sizedFprPct < 1.0, "擴容至 500,000 容量後假陽性率應低於 1%");

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("defaultExpectedInsertions", 10000);
        metrics.put("defaultAllocatedBits", saturatedBitSize);
        metrics.put("insertedElements", largeBatch.size());
        metrics.put("singleBatchAddMs", roundTwoDecimals(singleBatchAddMs));
        metrics.put("saturatedFalsePositiveRatePct", roundTwoDecimals(saturatedFprPct));
        metrics.put("sizedExpectedInsertions", 500000);
        metrics.put("sizedAllocatedBits", sizedBitSize);
        metrics.put("sizedFalsePositiveRatePct", roundTwoDecimals(sizedFprPct));
        PROBE_REPORT.put("probe3_bloomFilterBitSaturation", metrics);

        log.info("[Probe 3] 150,000 筆寫入預設 10,000 容量 ({} bits): 單次阻塞={} ms, 假陽性穿透率={}% ({}/{}) | 擴容 500,000 ({} bits) 假陽性率={}% ({}/{})",
                saturatedBitSize, roundTwoDecimals(singleBatchAddMs),
                roundTwoDecimals(saturatedFprPct), saturatedFalsePositives, attackSamples,
                sizedBitSize, roundTwoDecimals(sizedFprPct), sizedFalsePositives, attackSamples);
    }

    @Test
    @Order(4)
    @DisplayName("探針 4：ApiPathArgumentResolver 未初始化 ParameterNameDiscoverer 導致 GET /{id} 解析為 null")
    void probe4ApiPathArgumentResolverNullParameterNameBug() throws Exception {
        Method endpointMethod = TargetedBottleneckProbeIT.class.getDeclaredMethod("sampleGetByIdEndpoint", UUID.class);
        MethodParameter uninitializedParam = new MethodParameter(endpointMethod, 0);

        UUID expectedId = UUID.fromString("00000000-0000-0000-0002-000000000001");
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/users/" + expectedId);
        Map<String, String> uriVariables = new HashMap<>();
        uriVariables.put("id", expectedId.toString());
        servletRequest.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, uriVariables);
        ServletWebRequest webRequest = new ServletWebRequest(servletRequest);

        ApiPathArgumentResolver resolver = new ApiPathArgumentResolver();
        assertTrue(resolver.supportsParameter(uninitializedParam));

        // 驗證：未初始化 ParameterNameDiscoverer 時，原始 MethodParameter.getParameterName() 為 null
        String rawParamNameBeforeResolve = uninitializedParam.getParameterName();
        assertNull(rawParamNameBeforeResolve, "未初始化 ParameterNameDiscoverer 時原始 getParameterName() 為 null");

        // 修復後：ApiPathArgumentResolver 自動初始化 DefaultParameterNameDiscoverer 並正確解析 UUID
        DefaultDataBinderFactory binderFactory = new DefaultDataBinderFactory(null);
        Object resolvedValue = resolver.resolveArgument(uninitializedParam, null, webRequest, binderFactory);
        assertEquals(expectedId, resolvedValue, "ApiPathArgumentResolver 自動初始化 ParameterNameDiscoverer 後應正確解析出 UUID");

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("rawParameterNameBeforeDiscoverer", rawParamNameBeforeResolve);
        metrics.put("resolvedParameterNameAfterFix", uninitializedParam.getParameterName());
        metrics.put("resolvedValueAfterFix", resolvedValue.toString());
        PROBE_REPORT.put("probe4_apiPathArgumentResolverNullIdBug", metrics);

        log.info("[Probe 4] 原始未初始化 getParameterName()={} | 修復後 ApiPathArgumentResolver 解析結果={}",
                rawParamNameBeforeResolve, resolvedValue);
    }

    @Test
    @Order(5)
    @DisplayName("探針 5：JobPostingService 爬蟲職缺比對 O(N*M) 線性掃描 vs O(N+M) 雜湊索引耗時對比")
    void probe5JobMatchingQuadraticLinearScanVsHashMapLookup() {
        int existingCount = 5000;
        int scrapedCount = 2000;
        LocalDate targetDate = LocalDate.of(2026, 10, 25);

        record JobProbeRecord(String title, String companyName, LocalDate publicationDate) {
        }

        List<JobProbeRecord> existingJobs = new ArrayList<>(existingCount);
        for (int index = 0; index < existingCount; index++) {
            existingJobs.add(new JobProbeRecord("Backend Engineer " + index, "TSMC", targetDate));
        }

        List<JobProbeRecord> scrapedJobs = new ArrayList<>(scrapedCount);
        for (int index = 0; index < scrapedCount; index++) {
            scrapedJobs.add(new JobProbeRecord("Backend Engineer " + (existingCount - 1 - index), "tsmc", targetDate));
        }

        long quadraticStartNanos = System.nanoTime();
        int quadraticMatches = 0;
        for (JobProbeRecord scraped : scrapedJobs) {
            JobProbeRecord matched = existingJobs.stream()
                    .filter(existing -> existing.title().equalsIgnoreCase(scraped.title())
                            && existing.companyName().equalsIgnoreCase(scraped.companyName())
                            && existing.publicationDate().equals(scraped.publicationDate()))
                    .findFirst()
                    .orElse(null);
            if (matched != null) {
                quadraticMatches++;
            }
        }
        long quadraticElapsedNanos = System.nanoTime() - quadraticStartNanos;

        long hashStartNanos = System.nanoTime();
        Map<String, JobProbeRecord> jobIndexMap = new HashMap<>(existingCount * 2);
        for (JobProbeRecord existing : existingJobs) {
            String compositeKey = existing.title().toLowerCase(Locale.ROOT) + "|"
                    + existing.companyName().toLowerCase(Locale.ROOT) + "|"
                    + existing.publicationDate();
            jobIndexMap.putIfAbsent(compositeKey, existing);
        }
        int hashMatches = 0;
        for (JobProbeRecord scraped : scrapedJobs) {
            String lookupKey = scraped.title().toLowerCase(Locale.ROOT) + "|"
                    + scraped.companyName().toLowerCase(Locale.ROOT) + "|"
                    + scraped.publicationDate();
            if (jobIndexMap.containsKey(lookupKey)) {
                hashMatches++;
            }
        }
        long hashElapsedNanos = System.nanoTime() - hashStartNanos;

        assertEquals(scrapedCount, quadraticMatches);
        assertEquals(scrapedCount, hashMatches);

        double quadraticMs = quadraticElapsedNanos / 1_000_000.0;
        double hashMs = hashElapsedNanos / 1_000_000.0;
        double speedup = quadraticMs / Math.max(hashMs, 0.01);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("existingJobsCount", existingCount);
        metrics.put("scrapedJobsCount", scrapedCount);
        metrics.put("quadraticComparisons", (long) existingCount * scrapedCount);
        metrics.put("quadraticLinearScanMs", roundTwoDecimals(quadraticMs));
        metrics.put("hashMapIndexLookupMs", roundTwoDecimals(hashMs));
        metrics.put("speedupFactor", roundTwoDecimals(speedup));
        PROBE_REPORT.put("probe5_jobMatchingQuadraticVsHash", metrics);

        log.info("[Probe 5] Job 比對 (N={}, M={}): O(N*M) 線性掃描={} ms vs O(N+M) HashMap={} ms | 加速比: {}x",
                existingCount, scrapedCount, roundTwoDecimals(quadraticMs), roundTwoDecimals(hashMs), roundTwoDecimals(speedup));
    }

    @Test
    @Order(6)
    @DisplayName("探針 6：PostgreSQL 50,000 筆分頁預設 ORDER BY created_time DESC 無索引 Seq Scan vs B-Tree 索引對比")
    void probe6PostgresPaginationMissingCreatedTimeIndexSeqScanVsIndexScan() throws Exception {
        String jdbcUrl = SharedContainers.getPostgresqlContainer().getJdbcUrl();
        String username = SharedContainers.getPostgresqlContainer().getUsername();
        String password = SharedContainers.getPostgresqlContainer().getPassword();

        int rowCount = 50_000;
        int queryIterations = 25;

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             Statement statement = connection.createStatement()) {

            statement.execute("DROP TABLE IF EXISTS probe_paginated_entity");
            statement.execute("""
                    CREATE TABLE probe_paginated_entity (
                        id uuid PRIMARY KEY,
                        name varchar(255) NOT NULL,
                        description text,
                        created_time timestamp NOT NULL,
                        updated_time timestamp NOT NULL
                    )
                    """);
            statement.execute("""
                    INSERT INTO probe_paginated_entity (id, name, description, created_time, updated_time)
                    SELECT
                        ('00000000-0000-0000-0009-' || lpad(gs::text, 12, '0'))::uuid,
                        'Entity_' || gs,
                        repeat('PayloadData_', 12),
                        timestamp '2026-01-01 00:00:00' + (gs || ' seconds')::interval,
                        timestamp '2026-01-01 00:00:00' + (gs || ' seconds')::interval
                    FROM generate_series(1, 50000) AS gs
                    """);
            statement.execute("ANALYZE probe_paginated_entity");

            String explainPlanWithoutIndex = runExplainAnalyze(
                    connection,
                    "EXPLAIN (ANALYZE, BUFFERS) SELECT id, name, description, created_time FROM probe_paginated_entity ORDER BY created_time DESC LIMIT 20 OFFSET 0"
            );

            long noIndexStartNanos = System.nanoTime();
            for (int iter = 0; iter < queryIterations; iter++) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT id, name, description, created_time FROM probe_paginated_entity ORDER BY created_time DESC LIMIT 20 OFFSET 0");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        assertNotNull(rs.getObject(1));
                    }
                }
            }
            long noIndexElapsedNanos = System.nanoTime() - noIndexStartNanos;

            statement.execute("CREATE INDEX idx_probe_created_time_desc ON probe_paginated_entity (created_time DESC)");
            statement.execute("ANALYZE probe_paginated_entity");

            String explainPlanWithIndex = runExplainAnalyze(
                    connection,
                    "EXPLAIN (ANALYZE, BUFFERS) SELECT id, name, description, created_time FROM probe_paginated_entity ORDER BY created_time DESC LIMIT 20 OFFSET 0"
            );

            long indexedStartNanos = System.nanoTime();
            for (int iter = 0; iter < queryIterations; iter++) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT id, name, description, created_time FROM probe_paginated_entity ORDER BY created_time DESC LIMIT 20 OFFSET 0");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        assertNotNull(rs.getObject(1));
                    }
                }
            }
            long indexedElapsedNanos = System.nanoTime() - indexedStartNanos;

            statement.execute("DROP TABLE IF EXISTS probe_paginated_entity");

            double avgNoIndexMs = (noIndexElapsedNanos / (double) queryIterations) / 1_000_000.0;
            double avgIndexedMs = (indexedElapsedNanos / (double) queryIterations) / 1_000_000.0;
            double speedup = avgNoIndexMs / Math.max(avgIndexedMs, 0.01);

            assertTrue(explainPlanWithoutIndex.contains("Seq Scan"), "無索引時應觸發 Seq Scan + Top-N Heapsort");
            assertTrue(explainPlanWithIndex.contains("Index Scan"), "建立索引後應走 Index Scan Backward");

            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("tableRowCount", rowCount);
            metrics.put("avgSeqScanLatencyMs", roundTwoDecimals(avgNoIndexMs));
            metrics.put("avgIndexScanLatencyMs", roundTwoDecimals(avgIndexedMs));
            metrics.put("speedupFactor", roundTwoDecimals(speedup));
            metrics.put("explainWithoutIndex", explainPlanWithoutIndex);
            metrics.put("explainWithIndex", explainPlanWithIndex);
            PROBE_REPORT.put("probe6_postgresOrderByCreatedTimeIndex", metrics);

            log.info("[Probe 6] PostgreSQL 50,000 筆 ORDER BY created_time DESC LIMIT 20: 無索引 Seq Scan={} ms vs B-Tree Index Scan={} ms | 加速比: {}x",
                    roundTwoDecimals(avgNoIndexMs), roundTwoDecimals(avgIndexedMs), roundTwoDecimals(speedup));
        }

        writeProbeReportJson();
    }

    private String runExplainAnalyze(Connection connection, String sql) throws Exception {
        StringBuilder planBuilder = new StringBuilder();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                if (!planBuilder.isEmpty()) {
                    planBuilder.append(" | ");
                }
                planBuilder.append(resultSet.getString(1));
            }
        }
        return planBuilder.toString();
    }

    private double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private void writeProbeReportJson() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        String jsonContent = objectMapper.writeValueAsString(PROBE_REPORT);

        Path moduleReportDir = Paths.get("target", "k6-reports");
        Files.createDirectories(moduleReportDir);
        Files.writeString(moduleReportDir.resolve("targeted-bottleneck-probes.json"), jsonContent, StandardCharsets.UTF_8);

        Path rootReportDir = Paths.get("..", "target", "k6-reports");
        if (Files.exists(Paths.get("..", "pom.xml"))) {
            Files.createDirectories(rootReportDir);
            Files.writeString(rootReportDir.resolve("targeted-bottleneck-probes.json"), jsonContent, StandardCharsets.UTF_8);
        }
    }
}
