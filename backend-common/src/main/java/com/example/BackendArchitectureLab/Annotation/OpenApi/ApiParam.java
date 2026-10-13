package com.example.BackendArchitectureLab.Annotation.OpenApi;

import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 專案標準 Controller 方法參數標記註解。
 * 專門用於 @PathVariable 與 @RequestParam 參數說明。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Parameter
public @interface ApiParam {

    @AliasFor(annotation = Parameter.class, attribute = "description")
    String description();

    @AliasFor(annotation = Parameter.class, attribute = "example")
    String example() default "";

    @AliasFor(annotation = Parameter.class, attribute = "required")
    boolean required() default true;
}
