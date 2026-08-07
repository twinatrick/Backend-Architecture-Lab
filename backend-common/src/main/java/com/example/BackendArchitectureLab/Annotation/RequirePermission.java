package com.example.BackendArchitectureLab.Annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 權限需求註解。三層權限路徑 = {微服務層, 資源層(第二層), 動作層(第三層)}。
 * <ul>
 *   <li>value()：動作（第三層），如 "View" / "Edit" / "EditAll"。</li>
 *   <li>layer()：第二層覆寫（僅類別層使用）。預設由 Controller 類名去除「Controller」後綴產生；
 *       當類別上明列 layer() 時，底下方法的第二層一律吃此值，可再被方法級 layer() 覆寫。</li>
 * </ul>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {
    String value() default "";

    String layer() default "";
}