import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { DEFAULT_BASE_URL, getAuthToken, buildHeaders, getStandardThresholds, handleSummary as customSummary } from './common.js';

const externalLatency = new Trend('external_endpoint_duration');

const targetVUs = parseInt(__ENV.VUS || '50', 10);
const duration = __ENV.DURATION || '30s';
const withCache = __ENV.WITH_CACHE !== 'false';

export const options = {
    scenarios: {
        external_stress: {
            executor: 'constant-vus',
            vus: targetVUs,
            duration: duration,
        },
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

    // 1. 取得所有 Bot 配置清單 (GET /api/external/config)
    const resConfig = http.get(`${baseUrl}/api/external/config`, params);
    externalLatency.add(resConfig.timings.duration);
    check(resConfig, {
        'Bot Config List 200': (r) => r.status === 200,
    });

    // 2. 查詢 API 使用量統計摘要 (GET /api/external/usage/summary)
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
