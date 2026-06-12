package com.example.BackendArchitectureLab.Feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "iam-service", contextId = "permissionCheckClient")
public interface PermissionCheckFeignClient {

    @PostMapping("/permission/inner/validate")
    boolean validatePermission(@RequestParam("email") String email,
                               @RequestParam("one") String one,
                               @RequestParam("two") String two,
                               @RequestParam("three") String three);
}
