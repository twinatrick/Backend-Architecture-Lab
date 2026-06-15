package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SpringdocConfig {

    @Bean
    public GroupedOpenApi myApi() {
        return GroupedOpenApi.builder()
                .group("BackendArchitectureLab")
                .pathsToMatch("/**")
                .pathsToExclude("/**/inner/**")
                .build();
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("BackendArchitectureLab").version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication", new SecurityScheme()
                                .name("Bearer Authentication")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    @Bean
    public OperationCustomizer apiControllerTagCustomizer() {
        return (operation, handlerMethod) -> {
            ApiControllerTag tag = handlerMethod.getBeanType().getAnnotation(ApiControllerTag.class);
            if (tag != null && !tag.name().isBlank()) {
                operation.getTags().clear();
                operation.addTagsItem(tag.name());
            }
            return operation;
        };
    }

    @Bean
    public OpenApiCustomizer apiControllerTagDefinitionCustomizer(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mapping) {
        return openApi -> {
            var tagMap = new LinkedHashMap<String, Tag>();
            for (var entry : mapping.getHandlerMethods().entrySet()) {
                HandlerMethod handler = entry.getValue();
                ApiControllerTag annotation = handler.getBeanType().getAnnotation(ApiControllerTag.class);
                if (annotation != null && !annotation.name().isBlank() && !tagMap.containsKey(annotation.name())) {
                    tagMap.put(annotation.name(), new Tag()
                            .name(annotation.name())
                            .description(annotation.description()));
                }
            }
            if (!tagMap.isEmpty()) {
                if (openApi.getTags() == null) {
                    openApi.setTags(new ArrayList<>());
                }
                for (Tag tag : tagMap.values()) {
                    if (openApi.getTags().stream().noneMatch(t -> t.getName().equals(tag.getName()))) {
                        openApi.getTags().add(tag);
                    }
                }
            }
        };
    }
}
