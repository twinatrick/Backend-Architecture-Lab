package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiPath;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
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

    private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

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

        parameter.initParameterNameDiscovery(PARAMETER_NAME_DISCOVERER);
        String name = StringUtils.hasText(apiPath.name()) ? apiPath.name() : parameter.getParameterName();
        if (!StringUtils.hasText(name)) {
            name = parameter.getParameter().getName();
        }
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            return null;
        }

        Map<String, String> uriVars = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String value = (uriVars != null && name != null) ? uriVars.get(name) : null;
        if (value == null && uriVars != null && uriVars.size() == 1 && !StringUtils.hasText(apiPath.name())) {
            value = uriVars.values().iterator().next();
        }

        if (binderFactory != null) {
            WebDataBinder binder = binderFactory.createBinder(webRequest, null, name);
            return binder.convertIfNecessary(value, parameter.getParameterType(), parameter);
        }
        return value;
    }
}
