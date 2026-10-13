package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiPath;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * Controller 專屬 @ApiPath 參數解析器。
 * 負責從 URI 範本路徑變數中提取參數並進行型別轉換。
 */
public class ApiPathArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(ApiPath.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) throws Exception {
        ApiPath apiPath = parameter.getParameterAnnotation(ApiPath.class);
        if (apiPath == null) {
            return null;
        }

        String name = StringUtils.hasText(apiPath.name()) ? apiPath.name() : parameter.getParameterName();
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            return null;
        }

        Map<String, String> uriVars = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String value = uriVars != null ? uriVars.get(name) : null;

        if (binderFactory != null) {
            WebDataBinder binder = binderFactory.createBinder(webRequest, null, name);
            return binder.convertIfNecessary(value, parameter.getParameterType(), parameter);
        }
        return value;
    }
}
