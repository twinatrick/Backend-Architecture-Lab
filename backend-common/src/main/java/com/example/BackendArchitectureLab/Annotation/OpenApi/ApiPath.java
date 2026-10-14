package com.example.BackendArchitectureLab.Annotation.OpenApi;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Controller 專屬 PathVariable 複合註解。
 * 一次封裝路徑變數解析與 Swagger API 規格。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Parameter(in = ParameterIn.PATH)
public @interface ApiPath {

    @AliasFor(annotation = Parameter.class, attribute = "name")
    String name() default "";

    @AliasFor(annotation = Parameter.class, attribute = "description")
    String description();

    @AliasFor(annotation = Parameter.class, attribute = "example")
    String example() default "";

    String format() default "";

    @AliasFor(annotation = Parameter.class, attribute = "required")
    boolean required() default true;
}
