package com.example.BackendArchitectureLab.Aop;

import java.util.Collections;
import java.util.Set;

/**
 * 權限驗證介面：定義微服務端點的三層權限路徑驗證契約。
 * IAM 服務提供本機資料庫實作 (LocalPermissionValidatorImpl)，
 * 非 IAM 微服務使用 Feign 委派實作 (DefaultPermissionValidator)。
 */
public interface LocalPermissionValidator {

    /**
     * 驗證使用者是否具備指定的權限路徑。
     *
     * @param email 使用者 email
     * @param one   權限路徑第一層
     * @param two   權限路徑第二層
     * @param three 權限路徑第三層
     * @return 是否具備權限
     */
    boolean validate(String email, String one, String two, String three);

    /**
     * 取得使用者的全部有效權限集合（包含通配符或模組路徑）。
     *
     * @param email 使用者 email
     * @return 授權路徑集合 (例如 {"Job:Company:View", "Competency:Project:View"} 或 {"*"} 表示全部放行)
     */
    default Set<String> getUserPermissions(String email) {
        return Collections.emptySet();
    }
}