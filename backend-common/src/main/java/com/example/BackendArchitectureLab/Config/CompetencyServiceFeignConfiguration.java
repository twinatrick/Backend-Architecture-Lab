package com.example.BackendArchitectureLab.Config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * CompetencyServiceFeignConfiguration - 呼叫 competency-service 的 Feign 專用設定（H-01）。
 * <p>
 * 僅套用於 @FeignClient(configuration = this)（此處為 CompetencyServiceFeignClient）；
 * 以共享密鑰 X-Internal-Token 標示呼叫端身份，供 competency 內網攔截器驗證。
 * token 未設定（空白）時不帶 header；competency 端採 fail-closed（未設定即 401），
 * 因此呼叫端與被呼叫端必須於部署時同時設定相同且非空白的 APP_INTERNAL_TOKEN。
 * 注意：依 Feign 慣例此類不標 @Configuration，避免被主 context 掃描而全域套用。
 */
public class CompetencyServiceFeignConfiguration {

    @Value("${app.internal.token:}")
    private String internalToken;

    @Bean
    public RequestInterceptor internalTokenInterceptor() {
        return requestTemplate -> {
            if (internalToken != null && !internalToken.isBlank()) {
                requestTemplate.header("X-Internal-Token", internalToken);
            }
        };
    }
}