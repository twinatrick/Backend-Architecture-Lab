package com.example.BackendArchitectureLab.Feign;

import com.example.BackendArchitectureLab.Service.IUserGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * UserGatewayImpl - IUserGateway 的 Feign 實作，委派 UserServiceFeignClient 進行跨服務呼叫
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
