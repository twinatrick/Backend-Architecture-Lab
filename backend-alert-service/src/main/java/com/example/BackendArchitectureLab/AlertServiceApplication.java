package com.example.BackendArchitectureLab;

import com.example.BackendArchitectureLab.Feign.CompetencyServiceFeignClient;
import com.example.BackendArchitectureLab.Feign.PermissionCheckFeignClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(clients = {PermissionCheckFeignClient.class, CompetencyServiceFeignClient.class})
@EnableJpaAuditing
@EnableScheduling
public class AlertServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AlertServiceApplication.class, args);
    }
}
