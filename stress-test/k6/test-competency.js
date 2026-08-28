import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { DEFAULT_BASE_URL, getAuthToken, buildHeaders, getStandardThresholds, handleSummary as customSummary } from './common.js';

const competencyLatency = new Trend('competency_endpoint_duration');

const targetVUs = parseInt(__ENV.VUS || '50', 10);
const duration = __ENV.DURATION || '30s';
const withCache = __ENV.WITH_CACHE !== 'false';

export const options = {
    scenarios: {
        competency_stress: {
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

    // 1. 搜尋專案列表 (POST /api/project/search)
    const projectSearchPayload = JSON.stringify({
        page: 0,
        size: 20,
        keyword: ''
    });
    const resProjSearch = http.post(`${baseUrl}/api/project/search`, projectSearchPayload, params);
    competencyLatency.add(resProjSearch.timings.duration);
    check(resProjSearch, {
        'Competency Project Search 200': (r) => r.status === 200,
    });

    // 2. 取得技能列表 (GET /api/skill/get)
    const resSkillGet = http.get(`${baseUrl}/api/skill/get`, params);
    competencyLatency.add(resSkillGet.timings.duration);
    check(resSkillGet, {
        'Competency Skill Get 200': (r) => r.status === 200,
    });

    // 3. 搜尋技能等級 (POST /api/skill/level/search)
    const levelSearchPayload = JSON.stringify({
        page: 0,
        size: 20,
        keyword: ''
    });
    const resLevelSearch = http.post(`${baseUrl}/api/skill/level/search`, levelSearchPayload, params);
    competencyLatency.add(resLevelSearch.timings.duration);
    check(resLevelSearch, {
        'Competency Skill Level Search 200': (r) => r.status === 200,
    });

    // 4. 取得專案全量列表 (GET /api/project/get)
    const resProjGet = http.get(`${baseUrl}/api/project/get`, params);
    competencyLatency.add(resProjGet.timings.duration);
    check(resProjGet, {
        'Competency Project Get 200': (r) => r.status === 200,
    });

    sleep(0.05);
}

export function handleSummary(data) {
    return customSummary(data);
}
