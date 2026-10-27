import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { DEFAULT_BASE_URL, getAuthToken, buildHeaders, getStandardThresholds, handleSummary as customSummary } from './common.js';

const iamReqCount = new Counter('suite_iam_requests');
const competencyReqCount = new Counter('suite_competency_requests');
const jobReqCount = new Counter('suite_job_requests');
const alertReqCount = new Counter('suite_alert_requests');
const externalReqCount = new Counter('suite_external_requests');

const e2eDuration = new Trend('suite_e2e_req_duration');

const targetVUs = parseInt(__ENV.VUS || '50', 10);
const duration = __ENV.DURATION || '30s';
const withCache = __ENV.WITH_CACHE !== 'false';

export const options = {
    scenarios: {
        all_microservices_suite: {
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
        throw new Error('無法取得 IAM 認證 Token，全鏈路測試終止');
    }
    return { token };
}

export default function (data) {
    const params = buildHeaders(data.token);
    const baseUrl = DEFAULT_BASE_URL;

    // 依隨機權重分流各微服務 (35% IAM, 25% Competency, 20% Job, 10% Alert, 10% External)
    const rand = Math.random();

    if (rand < 0.35) {
        // --- 35% IAM 服務 ---
        iamReqCount.add(1);
        const searchPayload = JSON.stringify({ page: 0, size: 20 });
        const res = http.post(`${baseUrl}/api/users/search`, searchPayload, params);
        e2eDuration.add(res.timings.duration);
        check(res, { 'IAM Users Search 200': (r) => r.status === 200 });

        const rolePayload = JSON.stringify({ page: 0, size: 20 });
        const resRole = http.post(`${baseUrl}/api/role/search`, rolePayload, params);
        e2eDuration.add(resRole.timings.duration);
        check(resRole, { 'IAM Role Search 200': (r) => r.status === 200 });
    } else if (rand < 0.60) {
        // --- 25% Competency 職能服務 ---
        competencyReqCount.add(1);
        const projPayload = JSON.stringify({ page: 0, size: 20 });
        const res = http.post(`${baseUrl}/api/project/search`, projPayload, params);
        e2eDuration.add(res.timings.duration);
        check(res, { 'Competency Project Search 200': (r) => r.status === 200 });

        const skillPayload = JSON.stringify({ page: 0, size: 20 });
        const resSkill = http.post(`${baseUrl}/api/skill/search`, skillPayload, params);
        e2eDuration.add(resSkill.timings.duration);
        check(resSkill, { 'Competency Skill Search 200': (r) => r.status === 200 });
    } else if (rand < 0.80) {
        // --- 20% Job 職缺服務 ---
        jobReqCount.add(1);
        const jobPayload = JSON.stringify({ page: 0, size: 20 });
        const res = http.post(`${baseUrl}/api/job-posting/search`, jobPayload, params);
        e2eDuration.add(res.timings.duration);
        check(res, { 'Job Posting Search 200': (r) => r.status === 200 });

        const compPayload = JSON.stringify({ page: 0, size: 20, keyword: 'TSMC' });
        const resComp = http.post(`${baseUrl}/api/company/search`, compPayload, params);
        e2eDuration.add(resComp.timings.duration);
        check(resComp, { 'Job Company Search 200': (r) => r.status === 200 });
    } else if (rand < 0.90) {
        // --- 10% Alert 告警與感測服務 ---
        alertReqCount.add(1);
        const resCol = http.get(`${baseUrl}/api/aquarkData/getColumnNameList`, params);
        e2eDuration.add(resCol.timings.duration);
        check(resCol, { 'Alert Column Get 200': (r) => r.status === 200 });

        const aquarkPayload = JSON.stringify({ page: 0, size: 20 });
        const resAquark = http.post(`${baseUrl}/api/aquarkData/search`, aquarkPayload, params);
        e2eDuration.add(resAquark.timings.duration);
        check(resAquark, { 'Alert Aquark Search 200': (r) => r.status === 200 });
    } else {
        // --- 10% External 外部整合服務 ---
        externalReqCount.add(1);
        const resConfig = http.post(`${baseUrl}/api/external/config/search`, JSON.stringify({}), params);
        e2eDuration.add(resConfig.timings.duration);
        check(resConfig, { 'External Config Search 200': (r) => r.status === 200 });

        const startDate = '2020-01-01T00:00:00.000Z';
        const endDate = '2030-01-01T00:00:00.000Z';
        const resUsage = http.get(`${baseUrl}/api/external/usage/summary?start=${encodeURIComponent(startDate)}&end=${encodeURIComponent(endDate)}`, params);
        e2eDuration.add(resUsage.timings.duration);
        check(resUsage, { 'External Usage Summary 200': (r) => r.status === 200 });
    }

    sleep(0.05);
}

export function handleSummary(data) {
    return customSummary(data);
}
