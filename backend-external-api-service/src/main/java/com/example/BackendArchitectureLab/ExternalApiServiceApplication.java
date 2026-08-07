package com.example.BackendArchitectureLab;

import com.example.BackendArchitectureLab.Feign.AiPyServiceFeignClient;
import com.example.BackendArchitectureLab.Feign.PermissionCheckFeignClient;
import com.example.BackendArchitectureLab.Feign.UserServiceFeignClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(clients = {
        AiPyServiceFeignClient.class,
        PermissionCheckFeignClient.class,
        UserServiceFeignClient.class
})
public class ExternalApiServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExternalApiServiceApplication.class, args);
    }
}
