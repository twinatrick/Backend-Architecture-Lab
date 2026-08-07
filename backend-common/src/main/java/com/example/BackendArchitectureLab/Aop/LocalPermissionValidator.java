package com.example.BackendArchitectureLab.Aop;

import com.example.BackendArchitectureLab.Feign.PermissionCheckFeignClient;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 權限驗證抽象父類：預設以 Feign 呼叫 IAM 驗證。
 * IAM 服務可繼承並覆寫 validate() 為本機驗證，無需變更任何呼叫端程式碼。
 */
public abstract class LocalPermissionValidator {

    // required=false：IAM 環境下（已移除 @EnableFeignClients）無此 Feign bean，
    // 因覆寫 validate() 不會使用到此欄位，注入失敗也不致影響啟動。
    @Autowired(required = false)
    protected PermissionCheckFeignClient permissionCheckFeignClient;

    /**
     * 預設實作：透過 Feign 呼叫 IAM 的權限驗證端點。
     *
     * @param email 使用者 email
     * @param one   權限路徑第一層
     * @param two   權限路徑第二層
     * @param three 權限路徑第三層
     * @return 是否具備權限
     */
    public boolean validate(String email, String one, String two, String three) {
        return permissionCheckFeignClient.validatePermission(email, one, two, three);
    }
}