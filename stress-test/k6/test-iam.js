import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { DEFAULT_BASE_URL, getAuthToken, buildHeaders, buildStressScenario, getStandardThresholds, handleSummary as customSummary } from './common.js';

const iamLatency = new Trend('iam_endpoint_duration');

const targetVUs = parseInt(__ENV.VUS || '50', 10);
const duration = __ENV.DURATION || '30s';
const withCache = __ENV.WITH_CACHE !== 'false';

export const options = {
    scenarios: {
        iam_stress: buildStressScenario(targetVUs, duration),
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

    // 1. 分頁搜尋使用者 (POST /api/users/search)
    const searchPayload = JSON.stringify({
        page: 0,
        size: 20
    });
    const resSearch = http.post(`${baseUrl}/api/users/search`, searchPayload, params);
    iamLatency.add(resSearch.timings.duration);
    check(resSearch, {
        'IAM Users Search 200': (r) => r.status === 200,
    });

    // 2. 分頁搜尋角色清單 (POST /api/role/search)
    const roleSearchPayload = JSON.stringify({
        page: 0,
        size: 20
    });
    const resRole = http.post(`${baseUrl}/api/role/search`, roleSearchPayload, params);
    iamLatency.add(resRole.timings.duration);
    check(resRole, {
        'IAM Role Search 200': (r) => r.status === 200,
    });

    // 3. 分頁搜尋功能與權限清單 (POST /api/function/search)
    const funcSearchPayload = JSON.stringify({
        page: 0,
        size: 20
    });
    const resFunc = http.post(`${baseUrl}/api/function/search`, funcSearchPayload, params);
    iamLatency.add(resFunc.timings.duration);
    check(resFunc, {
        'IAM Function Search 200': (r) => r.status === 200,
    });

    // 4. 批次取得使用者詳情 (POST /api/users/batch-query，驗證 BatchCacheHelper / Redis MGET)
    const batchUserPayload = JSON.stringify({
        ids: [
            '00000000-0000-0000-0002-000000000001',
            '00000000-0000-0000-0002-000000000002',
            '00000000-0000-0000-0002-000000000003',
            '00000000-0000-0000-0002-000000000004',
            '00000000-0000-0000-0002-000000000005'
        ]
    });
    const resUser = http.post(`${baseUrl}/api/users/batch-query`, batchUserPayload, params);
    iamLatency.add(resUser.timings.duration);
    check(resUser, {
        'IAM Users Batch Query 200': (r) => r.status === 200,
    });

    sleep(0.05); // 模擬極輕微思考時間或網路間隔
}

export function handleSummary(data) {
    return customSummary(data);
}
