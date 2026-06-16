package com.example.BackendArchitectureLab.Security;

import com.example.BackendArchitectureLab.Annotation.Ingnore;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class IgnoreUrlsProvider implements InitializingBean {

    @Autowired
    private ApplicationContext applicationContext;
    private final Set<String> ignoredUrls = new HashSet<>();

    @Override
    public void afterPropertiesSet() {
        RequestMappingHandlerMapping mapping = applicationContext.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = mapping.getHandlerMethods();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            HandlerMethod handlerMethod = entry.getValue();
            if (handlerMethod.hasMethodAnnotation(Ingnore.class)) {
                RequestMappingInfo mappingInfo = entry.getKey();
                if (mappingInfo.getPatternValues() != null) {
                    ignoredUrls.addAll(mappingInfo.getPatternValues());
                } else if (mappingInfo.getPathPatternsCondition() != null) {
                    mappingInfo.getPathPatternsCondition().getPatternValues().forEach(ignoredUrls::add);
                }
            }
        }

        ignoredUrls.add("/swagger-ui/**");
        ignoredUrls.add("/swagger-ui.html");
        ignoredUrls.add("/v3/api-docs/**");
        ignoredUrls.add("/api-docs/**");
        ignoredUrls.add("/swagger-resources/**");
        ignoredUrls.add("/webjars/**");
        ignoredUrls.add("/error");
        ignoredUrls.add("/actuator/**");

        ignoredUrls.add("/**/inner/**");
    }

    public String[] getIgnoredUrls() {
        return ignoredUrls.toArray(new String[0]);
    }
}
