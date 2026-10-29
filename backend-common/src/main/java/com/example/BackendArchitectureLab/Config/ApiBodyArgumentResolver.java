package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiBody;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Validator;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Controller 專屬 @ApiBody 參數解析器。
 * 負責反序列化 JSON 請求體並自動執行 JSR-380 資料檢驗。
 */
@RequiredArgsConstructor
public class ApiBodyArgumentResolver implements HandlerMethodArgumentResolver {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(ApiBody.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) throws Exception {
        HttpServletRequest servletRequest = webRequest.getNativeRequest(HttpServletRequest.class);
        if (servletRequest == null) {
            return null;
        }

        if (parameter.getParameterType() == byte[].class) {
            return servletRequest.getInputStream().readAllBytes();
        }

        JavaType javaType = objectMapper.constructType(parameter.getGenericParameterType());
        Object target;
        try {
            target = objectMapper.readValue(servletRequest.getInputStream(), javaType);
        } catch (Exception e) {
            throw new HttpMessageNotReadableException("JSON 反序列化失敗: " + e.getMessage(), e, new ServletServerHttpRequest(servletRequest));
        }

        if (target != null && binderFactory != null) {
            WebDataBinder binder = binderFactory.createBinder(webRequest, target, parameter.getParameterName());
            if (validator != null) {
                binder.addValidators(validator);
            }
            binder.validate();
            BindingResult bindingResult = binder.getBindingResult();
            if (bindingResult.hasErrors()) {
                throw new MethodArgumentNotValidException(parameter, bindingResult);
            }
        }
        return target;
    }
}
