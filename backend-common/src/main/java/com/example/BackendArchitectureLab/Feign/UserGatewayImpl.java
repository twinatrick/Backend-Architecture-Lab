package com.example.BackendArchitectureLab.Feign;

import com.example.BackendArchitectureLab.Service.IUserGateway;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * UserGatewayImpl - IUserGateway 的 Feign 實作，委派 UserServiceFeignClient 進行跨服務呼叫。
 * <p>
 * 採用 {@code Optional<UserServiceFeignClient>} 建構子注入：
 * 本類位於 backend-common，所有服務（含 iam-service 等未啟用 {@code @EnableFeignClients} 的服務）都會載入；
 * `UserServiceFeignClient` bean 僅存在於啟用 Feign 的服務中。
 * 透過 Optional 建構子注入維持純粹的 Constructor Injection，並在呼叫時進行存在性防護。
 */
@Component
public class UserGatewayImpl implements IUserGateway {

    private final UserServiceFeignClient userServiceFeignClient;

    public UserGatewayImpl(Optional<UserServiceFeignClient> userServiceFeignClientOptional) {
        this.userServiceFeignClient = userServiceFeignClientOptional.orElse(null);
    }

    @Override
    public boolean existsUserById(UUID id) {
        if (userServiceFeignClient == null) {
            throw new IllegalStateException("UserServiceFeignClient is not available in this service");
        }
        return userServiceFeignClient.existsUserById(id);
    }
}
