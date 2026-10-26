import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { DEFAULT_BASE_URL, getAuthToken, buildHeaders, buildStressScenario, getStandardThresholds, handleSummary as customSummary } from './common.js';

const competencyLatency = new Trend('competency_endpoint_duration');

const targetVUs = parseInt(__ENV.VUS || '50', 10);
const duration = __ENV.DURATION || '30s';
const withCache = __ENV.WITH_CACHE !== 'false';

export const options = {
    scenarios: {
        competency_stress: buildStressScenario(targetVUs, duration),
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

    // 2. 分頁搜尋技能列表 (POST /api/skill/search)
    const skillSearchPayload = JSON.stringify({
        page: 0,
        size: 20
    });
    const resSkillSearch = http.post(`${baseUrl}/api/skill/search`, skillSearchPayload, params);
    competencyLatency.add(resSkillSearch.timings.duration);
    check(resSkillSearch, {
        'Competency Skill Search 200': (r) => r.status === 200,
    });

    // 3. 搜尋技能等級 (POST /api/skill/level/search)
    const levelSearchPayload = JSON.stringify({
        page: 0,
        size: 20
    });
    const resLevelSearch = http.post(`${baseUrl}/api/skill/level/search`, levelSearchPayload, params);
    competencyLatency.add(resLevelSearch.timings.duration);
    check(resLevelSearch, {
        'Competency Skill Level Search 200': (r) => r.status === 200,
    });

    // 4. 批次取得專案詳情 (POST /api/project/batch-query，驗證 BatchCacheHelper / Redis MGET)
    const batchProjPayload = JSON.stringify({
        ids: [
            '00000000-0000-0000-0004-000000000001',
            '00000000-0000-0000-0004-000000000002',
            '00000000-0000-0000-0004-000000000003',
            '00000000-0000-0000-0004-000000000004',
            '00000000-0000-0000-0004-000000000005'
        ]
    });
    const resProjById = http.post(`${baseUrl}/api/project/batch-query`, batchProjPayload, params);
    competencyLatency.add(resProjById.timings.duration);
    check(resProjById, {
        'Competency Project Batch Query 200': (r) => r.status === 200,
    });

    sleep(0.05);
}

export function handleSummary(data) {
    return customSummary(data);
}
