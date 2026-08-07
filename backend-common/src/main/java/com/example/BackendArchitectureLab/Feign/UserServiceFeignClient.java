package com.example.BackendArchitectureLab.Feign;

import com.example.BackendArchitectureLab.Vo.UserVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "iam-service")
public interface UserServiceFeignClient {

    @PostMapping("/users/inner/by-email")
    UserVo getUserByEmail(@RequestParam("email") String email);

    @GetMapping("/users/inner/exists/{id}")
    boolean existsUserById(@PathVariable("id") UUID id);
}
