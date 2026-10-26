import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { DEFAULT_BASE_URL, getAuthToken, buildHeaders, buildStressScenario, getStandardThresholds, handleSummary as customSummary } from './common.js';

const externalLatency = new Trend('external_endpoint_duration');

const targetVUs = parseInt(__ENV.VUS || '50', 10);
const duration = __ENV.DURATION || '30s';
const withCache = __ENV.WITH_CACHE !== 'false';

export const options = {
    scenarios: {
        external_stress: buildStressScenario(targetVUs, duration),
    },
    thresholds: getStandardThresholds(withCache),
};

export function setup() {
    const token = getAuthToken(DEFAULT_BASE_URL);
    if (!token) {
        throw new Error('無法取得 IAM 認證 Token，測試終止');
    }
    return { token };
}

export default function (data) {
    const params = buildHeaders(data.token);
    const baseUrl = DEFAULT_BASE_URL;

    // 1. 取得 Bot 配置清單 (POST /api/external/config/search)
    const resConfig = http.post(`${baseUrl}/api/external/config/search`, JSON.stringify({ page: 0, size: 20 }), params);
    externalLatency.add(resConfig.timings.duration);
    check(resConfig, {
        'Bot Config Search 200': (r) => r.status === 200,
    });

    // 2. 批次查詢 Bot 配置詳情 (POST /api/external/config/batch-query - 驗證 botConfigs 快取命中)
    const resBatch = http.post(
        `${baseUrl}/api/external/config/batch-query`,
        JSON.stringify({ ids: ['00000000-0000-0000-0000-000000000601'] }),
        params
    );
    externalLatency.add(resBatch.timings.duration);
    check(resBatch, {
        'Bot Config Batch Query 200': (r) => r.status === 200,
    });

    // 3. 查詢 API 使用量統計摘要 (GET /api/external/usage/summary)
    const resUsage = http.get(`${baseUrl}/api/external/usage/summary?start=2020-01-01T00:00:00Z&end=2030-01-01T00:00:00Z`, params);
    externalLatency.add(resUsage.timings.duration);
    check(resUsage, {
        'API Usage Summary 200': (r) => r.status === 200,
    });

    sleep(0.05);
}

export function handleSummary(data) {
    return customSummary(data);
}
