import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { DEFAULT_BASE_URL, getAuthToken, buildHeaders, getStandardThresholds, handleSummary as customSummary } from './common.js';

const iamLatency = new Trend('iam_endpoint_duration');

const targetVUs = parseInt(__ENV.VUS || '50', 10);
const duration = __ENV.DURATION || '30s';
const withCache = __ENV.WITH_CACHE !== 'false';

export const options = {
    scenarios: {
        iam_stress: {
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

    // 1. 搜尋使用者 (分頁與關鍵字查詢)
    const searchPayload = JSON.stringify({
        page: 0,
        size: 20,
        keyword: 'Employee'
    });
    const resSearch = http.post(`${baseUrl}/api/users/search`, searchPayload, params);
    iamLatency.add(resSearch.timings.duration);
    check(resSearch, {
        'IAM Users Search 200': (r) => r.status === 200,
    });

    // 2. 查詢角色清單 (高快取命中情境)
    const resRole = http.post(`${baseUrl}/api/role/get`, '{}', params);
    iamLatency.add(resRole.timings.duration);
    check(resRole, {
        'IAM Role Get 200': (r) => r.status === 200,
    });

    // 3. 查詢功能與權限樹
    const resFunc = http.get(`${baseUrl}/api/function/get`, params);
    iamLatency.add(resFunc.timings.duration);
    check(resFunc, {
        'IAM Function Get 200': (r) => r.status === 200,
    });

    // 4. 依 ID 取得使用者詳情
    const resUser = http.get(`${baseUrl}/api/users/00000000-0000-0000-0000-000000000001`, params);
    iamLatency.add(resUser.timings.duration);
    check(resUser, {
        'IAM User By ID 200': (r) => r.status === 200,
    });

    sleep(0.05); // 模擬極輕微思考時間或網路間隔
}

export function handleSummary(data) {
    return customSummary(data);
}
