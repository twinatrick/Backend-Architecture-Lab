import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { DEFAULT_BASE_URL, getAuthToken, buildHeaders, buildStressScenario, getStandardThresholds, handleSummary as customSummary } from './common.js';

const alertLatency = new Trend('alert_endpoint_duration');

const targetVUs = parseInt(__ENV.VUS || '50', 10);
const duration = __ENV.DURATION || '30s';
const withCache = __ENV.WITH_CACHE !== 'false';

export const options = {
    scenarios: {
        alert_stress: buildStressScenario(targetVUs, duration),
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

    // 1. 取得感測器欄位清單 (GET /api/aquarkData/getColumnNameList)
    const resCol = http.get(`${baseUrl}/api/aquarkData/getColumnNameList`, params);
    alertLatency.add(resCol.timings.duration);
    check(resCol, {
        'Aquark Column Names 200': (r) => r.status === 200,
    });

    // 2. 分頁搜尋感測器水情資料 (POST /api/aquarkData/search)
    const aquarkSearchPayload = JSON.stringify({
        page: 0,
        size: 20
    });
    const resAquarkSearch = http.post(`${baseUrl}/api/aquarkData/search`, aquarkSearchPayload, params);
    alertLatency.add(resAquarkSearch.timings.duration);
    check(resAquarkSearch, {
        'Aquark Data Search 200': (r) => r.status === 200,
    });

    // 3. 批次查詢告警門檻 (POST /api/alertCheckLimit/batch-query，驗證 BatchCacheHelper / Redis MGET)
    const batchLimitPayload = JSON.stringify({
        ids: [
            '00000000-0000-0000-0000-000000000401',
            '00000000-0000-0000-0000-000000000402',
            '00000000-0000-0000-0000-000000000403'
        ]
    });
    const resLimitSearch = http.post(`${baseUrl}/api/alertCheckLimit/batch-query`, batchLimitPayload, params);
    alertLatency.add(resLimitSearch.timings.duration);
    check(resLimitSearch, {
        'Alert Check Limit Batch Query 200': (r) => r.status === 200,
    });

    // 4. 取得快取命中即時指標 (GET /api/cache-stats)
    const resCacheStats = http.get(`${baseUrl}/api/cache-stats`, params);
    alertLatency.add(resCacheStats.timings.duration);
    check(resCacheStats, {
        'Cache Stats 200': (r) => r.status === 200,
    });

    sleep(0.05);
}

export function handleSummary(data) {
    return customSummary(data);
}
