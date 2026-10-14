package com.example.BackendArchitectureLab.Config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.Validator;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 自動註冊 Controller 專屬複合註解之參數解析器。
 */
@Configuration
@RequiredArgsConstructor
public class ApiResolverWebMvcConfig implements WebMvcConfigurer {

    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private Validator validator;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new ApiBodyArgumentResolver(objectMapper, validator));
        resolvers.add(new ApiPathArgumentResolver());
        resolvers.add(new ApiQueryArgumentResolver());
    }
}
