package com.example.BackendArchitectureLab.Annotation.OpenApi;

import io.swagger.v3.oas.annotations.parameters.RequestBody;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Controller 專屬 Request Body 複合註解。
 * 一次封裝 JSON 請求體反序列化、Jakarta JSR-380 校驗與 Swagger API 規格。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@RequestBody
public @interface ApiBody {

    @AliasFor(annotation = RequestBody.class, attribute = "required")
    boolean required() default true;

    @AliasFor(annotation = RequestBody.class, attribute = "description")
    String description() default "";
}
