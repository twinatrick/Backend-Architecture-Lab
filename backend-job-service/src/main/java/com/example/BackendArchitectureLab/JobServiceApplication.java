package com.example.BackendArchitectureLab;

import com.example.BackendArchitectureLab.Feign.ExternalApiServiceFeignClient;
import com.example.BackendArchitectureLab.Feign.PermissionCheckFeignClient;
import com.example.BackendArchitectureLab.Feign.UserServiceFeignClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(clients = {
        ExternalApiServiceFeignClient.class,
        PermissionCheckFeignClient.class,
        UserServiceFeignClient.class
})
@EnableJpaAuditing
@EnableScheduling
public class JobServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(JobServiceApplication.class, args);
    }
}
