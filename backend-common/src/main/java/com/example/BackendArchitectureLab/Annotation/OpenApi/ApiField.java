package com.example.BackendArchitectureLab.Annotation.OpenApi;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 專案標準 DTO/VO 欄位標記註解。
 * 強制標註欄位中文說明、範例值、必填性與格式。
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Schema
public @interface ApiField {

    @AliasFor(annotation = Schema.class, attribute = "description")
    String description();

    @AliasFor(annotation = Schema.class, attribute = "example")
    String example() default "";

    @AliasFor(annotation = Schema.class, attribute = "requiredMode")
    Schema.RequiredMode requiredMode() default Schema.RequiredMode.AUTO;

    /**
     * 是否為必填欄位
     */
    boolean required() default false;

    @AliasFor(annotation = Schema.class, attribute = "format")
    String format() default "";
}
