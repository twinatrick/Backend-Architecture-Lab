package com.example.BackendArchitectureLab.Feign;

import com.example.BackendArchitectureLab.Service.IUserGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * UserGatewayImpl - IUserGateway 的 Feign 實作，委派 UserServiceFeignClient 進行跨服務呼叫。
 * <p>
 * 為何使用 {@code @Autowired(required = false)}（而非 constructor injection 的 fail-fast）：
 * 本類位於 backend-common，所有服務（含 iam-service 等未啟用 {@code @EnableFeignClients} 的服務）都會載入；
 * `UserServiceFeignClient` bean 僅存在於啟用 Feign 的服務中，required = true 會導致無 Feign 的服務啟動失敗。
 * 因此以 optional 注入 + 顯式 null 檢查（IllegalStateException）作為防護：
 * 呼叫端（僅 competency 的業務 Service）所在服務必然具備該 bean，其他服務載入本類只是靜態持有、不會呼叫。
 */
@Component
public class UserGatewayImpl implements IUserGateway {

    @Autowired(required = false)
    private UserServiceFeignClient userServiceFeignClient;

    @Override
    public boolean existsUserById(UUID id) {
        if (userServiceFeignClient == null) {
            throw new IllegalStateException("UserServiceFeignClient is not available in this service");
        }
        return userServiceFeignClient.existsUserById(id);
    }
}
