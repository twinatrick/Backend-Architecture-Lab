package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiQuery;
import org.springframework.core.MethodParameter;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Controller 專屬 @ApiQuery 參數解析器。
 * 負責從 HTTP 查詢參數中提取參數、支援預設值、必填檢查與型別轉換。
 */
public class ApiQueryArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(ApiQuery.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) throws Exception {
        ApiQuery apiQuery = parameter.getParameterAnnotation(ApiQuery.class);
        if (apiQuery == null) {
            return null;
        }

        String name = StringUtils.hasText(apiQuery.name()) ? apiQuery.name() : parameter.getParameterName();
        String value = webRequest.getParameter(name);

        if (value == null && StringUtils.hasText(apiQuery.defaultValue())) {
            value = apiQuery.defaultValue();
        }

        if (value == null && apiQuery.required()) {
            throw new MissingServletRequestParameterException(name, parameter.getParameterType().getSimpleName());
        }

        if (binderFactory != null) {
            WebDataBinder binder = binderFactory.createBinder(webRequest, null, name);
            return binder.convertIfNecessary(value, parameter.getParameterType(), parameter);
        }
        return value;
    }
}
