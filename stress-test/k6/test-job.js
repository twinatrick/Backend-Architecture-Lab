import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { DEFAULT_BASE_URL, getAuthToken, buildHeaders, getStandardThresholds, handleSummary as customSummary } from './common.js';

const jobLatency = new Trend('job_endpoint_duration');

const targetVUs = parseInt(__ENV.VUS || '50', 10);
const duration = __ENV.DURATION || '30s';
const withCache = __ENV.WITH_CACHE !== 'false';

export const options = {
    scenarios: {
        job_stress: {
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

    // 1. 搜尋職缺列表 (POST /api/job-posting/search)
    const jobPayload = JSON.stringify({
        page: 0,
        size: 20,
        keyword: ''
    });
    const resJobSearch = http.post(`${baseUrl}/api/job-posting/search`, jobPayload, params);
    jobLatency.add(resJobSearch.timings.duration);
    check(resJobSearch, {
        'Job Posting Search 200': (r) => r.status === 200,
    });

    // 2. 搜尋公司列表 (POST /api/company/search)
    const compPayload = JSON.stringify({
        page: 0,
        size: 20,
        keyword: 'TSMC'
    });
    const resCompSearch = http.post(`${baseUrl}/api/company/search`, compPayload, params);
    jobLatency.add(resCompSearch.timings.duration);
    check(resCompSearch, {
        'Company Search 200': (r) => r.status === 200,
    });

    // 3. 取得公司清單 (GET /api/company/get)
    const resCompGet = http.get(`${baseUrl}/api/company/get`, params);
    jobLatency.add(resCompGet.timings.duration);
    check(resCompGet, {
        'Company Get 200': (r) => r.status === 200,
    });

    // 4. 取得使用者職缺關聯清單 (GET /api/user-job-link/get)
    const resLinkGet = http.get(`${baseUrl}/api/user-job-link/get`, params);
    jobLatency.add(resLinkGet.timings.duration);
    check(resLinkGet, {
        'User Job Link Get 200': (r) => r.status === 200,
    });

    sleep(0.05);
}

export function handleSummary(data) {
    return customSummary(data);
}
