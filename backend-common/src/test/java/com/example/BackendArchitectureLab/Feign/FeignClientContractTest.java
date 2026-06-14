package com.example.BackendArchitectureLab.Feign;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FeignClientContractTest {

    private static final List<Class<?>> FEIGN_CLIENTS = List.of(
            UserServiceFeignClient.class,
            AlertCheckLimitFeignClient.class,
            ProjectServiceFeignClient.class,
            SkillServiceFeignClient.class,
            AiServiceFeignClient.class
    );

    private static final Set<Class<? extends Annotation>> HTTP_METHOD_ANNOTATIONS = Set.of(
            GetMapping.class, PostMapping.class, PutMapping.class,
            DeleteMapping.class, PatchMapping.class, RequestMapping.class
    );

    @Test
    @DisplayName("All FeignClients should have @FeignClient with a name")
    void allFeignClientsHaveAnnotation() {
        for (Class<?> client : FEIGN_CLIENTS) {
            FeignClient annotation = client.getAnnotation(FeignClient.class);
            assertNotNull(annotation, client.getSimpleName() + " missing @FeignClient");
            assertNotNull(annotation.name(), client.getSimpleName() + " @FeignClient name is null");
            assertFalse(annotation.name().isBlank(), client.getSimpleName() + " @FeignClient name is blank");
        }
    }

    @Test
    @DisplayName("Each method should have exactly one HTTP method annotation")
    void eachMethodHasHttpAnnotation() {
        for (Class<?> client : FEIGN_CLIENTS) {
            for (Method method : client.getDeclaredMethods()) {
                long httpAnnotationCount = Arrays.stream(method.getAnnotations())
                        .map(Annotation::annotationType)
                        .filter(HTTP_METHOD_ANNOTATIONS::contains)
                        .count();
                assertEquals(1, httpAnnotationCount,
                        client.getSimpleName() + "." + method.getName()
                                + " should have exactly 1 HTTP method annotation, found " + httpAnnotationCount);
            }
        }
    }

    @Test
    @DisplayName("Each method parameter should have a binding annotation")
    void eachParameterHasBindingAnnotation() {
        Set<Class<? extends Annotation>> bindingAnnotations = Set.of(
                PathVariable.class, RequestParam.class, RequestBody.class
        );
        for (Class<?> client : FEIGN_CLIENTS) {
            for (Method method : client.getDeclaredMethods()) {
                for (Parameter param : method.getParameters()) {
                    long bindingAnnotationCount = Arrays.stream(param.getAnnotations())
                            .map(Annotation::annotationType)
                            .filter(bindingAnnotations::contains)
                            .count();
                    assertEquals(1, bindingAnnotationCount,
                            client.getSimpleName() + "." + method.getName()
                                    + " parameter " + param.getName()
                                    + " should have exactly 1 binding annotation, found "
                                    + bindingAnnotationCount);
                }
            }
        }
    }

    @Test
    @DisplayName("All FeignClients should have unique name+contextId combinations")
    void allFeignClientNamesAreUnique() {
        List<String> identifiers = FEIGN_CLIENTS.stream()
                .map(c -> {
                    FeignClient ann = c.getAnnotation(FeignClient.class);
                    return ann.name() + ":" + ann.contextId();
                })
                .toList();
        assertEquals(identifiers.size(), identifiers.stream().distinct().count(),
                "FeignClient name:contextId must be unique: " + identifiers);
    }


}
