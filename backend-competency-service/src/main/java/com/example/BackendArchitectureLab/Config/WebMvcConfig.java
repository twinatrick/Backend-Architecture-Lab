package com.example.BackendArchitectureLab.Config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvcConfig - MVC 攔截器註冊。
 * <p>
 * H-01：對內網端點 /project/inner/** 套用 InternalApiTokenInterceptor，
 * 以共享 token 驗證 service-to-service 呼叫（defense-in-depth，不信任呼叫端自行宣告的 fencing token）。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private InternalApiTokenInterceptor internalApiTokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(internalApiTokenInterceptor)
                .addPathPatterns("/project/inner/**");
    }
}
