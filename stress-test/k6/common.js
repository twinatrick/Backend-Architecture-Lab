import http from 'k6/http';
import { check } from 'k6';

export const DEFAULT_BASE_URL = __ENV.BASE_URL || 'http://localhost:8000';
export const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || 'admin@tsmc.com';
export const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || 'admin';

/**
 * 取得 JWT 認證 Token
 */
export function getAuthToken(baseUrl = DEFAULT_BASE_URL, email = ADMIN_EMAIL, password = ADMIN_PASSWORD) {
    if (__ENV.AUTH_TOKEN) {
        return __ENV.AUTH_TOKEN;
    }

    const passwordsToTry = [password, 'admin', 'password'];
    for (const pwd of passwordsToTry) {
        const loginPayload = JSON.stringify({
            email: email,
            password: pwd
        });

        const params = {
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            timeout: '10s'
        };

        const res = http.post(`${baseUrl}/api/auth/login`, loginPayload, params);
        if (res.status === 200) {
            try {
                const body = JSON.parse(res.body);
                const token = body.accessToken || (body.data && body.data.accessToken);
                if (token) {
                    return token;
                }
            } catch (e) {}
        }
    }

    // 嘗試建立 superuser
    try {
        const superPayload = JSON.stringify({
            key: __ENV.SUPERUSER_KEY || 'super_secret_key_change_in_production',
            email: email
        });
        http.post(`${baseUrl}/api/auth/superuser`, superPayload, {
            headers: { 'Content-Type': 'application/json' },
            timeout: '10s'
        });

        // 再次嘗試登入
        const retryRes = http.post(`${baseUrl}/api/auth/login`, JSON.stringify({ email, password }), {
            headers: { 'Content-Type': 'application/json' },
            timeout: '10s'
        });
        if (retryRes.status === 200) {
            const body = JSON.parse(retryRes.body);
            const token = body.accessToken || (body.data && body.data.accessToken);
            if (token) return token;
        }
    } catch (e) {}

    // 嘗試註冊
    try {
        const signupRes = http.post(`${baseUrl}/api/auth/signup`, JSON.stringify({ email, password }), {
            headers: { 'Content-Type': 'application/json' },
            timeout: '10s'
        });
        if (signupRes.status === 200) {
            const body = JSON.parse(signupRes.body);
            const token = body.accessToken || (body.data && body.data.accessToken);
            if (token) return token;
        }
    } catch (e) {}

    console.error(`無法取得認證 Token`);
    return null;
}

/**
 * 構建帶有 Bearer Token 的標準 Headers
 */
export function buildHeaders(token) {
    const headers = {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        'Connection': 'keep-alive'
    };
    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }
    return { headers };
}

/**
 * 取得標準壓測閾值 (Thresholds)
 */
export function getStandardThresholds(withCache = true) {
    if (withCache) {
        return {
            http_req_failed: ['rate<0.01'], // 錯誤率低於 1%
            http_req_duration: ['p(95)<50', 'p(99)<100'], // 有快取：P95 < 50ms, P99 < 100ms
        };
    } else {
        return {
            http_req_failed: ['rate<0.01'],
            http_req_duration: ['p(95)<300', 'p(99)<600'], // 無快取：P95 < 300ms, P99 < 600ms
        };
    }
}

/**
 * 自訂壓測結果摘要報告
 */
export function handleSummary(data) {
    const vus = __ENV.VUS || '50';
    const mode = __ENV.WITH_CACHE === 'false' ? '無快取 (Disabled)' : '有快取 (Enabled)';
    const threadModel = __ENV.VIRTUAL_THREADS === 'false' ? 'Platform Threads' : 'Virtual Threads (Java 21)';

    console.log(`\n======================================================`);
    console.log(`🚀 [k6 壓測摘要] 併發 VUs: ${vus} | 快取狀態: ${mode} | 執行緒架構: ${threadModel}`);
    console.log(`------------------------------------------------------`);
    if (data.metrics && data.metrics.http_reqs && data.metrics.http_reqs.values) {
        console.log(`總請求數 (Total Requests): ${data.metrics.http_reqs.values.count || 0}`);
        console.log(`吞吐量 (Throughput QPS) : ${(data.metrics.http_reqs.values.rate || 0).toFixed(2)} req/s`);
    }
    if (data.metrics && data.metrics.http_req_duration && data.metrics.http_req_duration.values) {
        const d = data.metrics.http_req_duration.values;
        const avg = d.avg != null ? d.avg.toFixed(2) : 'N/A';
        const p90 = d['p(90)'] != null ? d['p(90)'].toFixed(2) : (d.med != null ? d.med.toFixed(2) : 'N/A');
        const p95 = d['p(95)'] != null ? d['p(95)'].toFixed(2) : (d.max != null ? d.max.toFixed(2) : 'N/A');
        const p99 = d['p(99)'] != null ? d['p(99)'].toFixed(2) : (d.max != null ? d.max.toFixed(2) : 'N/A');
        console.log(`平均延遲 (Avg Latency)  : ${avg} ms`);
        console.log(`P90 延遲 (P90 Latency)   : ${p90} ms`);
        console.log(`P95 延遲 (P95 Latency)   : ${p95} ms`);
        console.log(`P99 延遲 (P99 Latency)   : ${p99} ms`);
    }
    if (data.metrics && data.metrics.http_req_failed && data.metrics.http_req_failed.values) {
        const failRate = ((data.metrics.http_req_failed.values.rate || 0) * 100).toFixed(2);
        console.log(`失敗率 (Failure Rate)    : ${failRate}%`);
    }
    console.log(`======================================================\n`);

    return {};
}
