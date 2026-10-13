package com.example.BackendArchitectureLab.Annotation.OpenApi;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 專案標準 DTO/VO 類別標記註解。
 * 強制標註資料傳輸物件與視圖模型之業務領域說明。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Schema
public @interface ApiDto {

    @AliasFor(annotation = Schema.class, attribute = "description")
    String description();

    @AliasFor(annotation = Schema.class, attribute = "name")
    String name() default "";
}
