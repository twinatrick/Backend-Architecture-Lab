package com.example.BackendArchitectureLab.Feign;

import com.example.BackendArchitectureLab.Dto.Vo.RoleOutVo;
import com.example.BackendArchitectureLab.Dto.Vo.SignupRequest;
import com.example.BackendArchitectureLab.Dto.Vo.UserVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "iam-service")
public interface UserServiceFeignClient {

    @GetMapping("/users/inner/{id}")
    UserVo getUserById(@PathVariable("id") UUID id);

    @PostMapping("/users/inner/by-email")
    UserVo getUserByEmail(@RequestBody String email);

    @PostMapping("/users/create")
    UserVo createUser(@RequestBody SignupRequest request);

    @PostMapping("/users/inner/rebind-projects")
    void rebindUserProjects(@RequestParam("userId") UUID userId,
                            @RequestBody List<UUID> projectIds);

    @GetMapping("/users/inner/exists/{id}")
    boolean existsUserById(@PathVariable("id") UUID id);

    @GetMapping("/users/inner/by-email-exists")
    boolean existsUserByEmail(@RequestParam("email") String email);

    @GetMapping("/role/inner/all")
    List<RoleOutVo> getAllRoles();

    @GetMapping("/role/inner/by-name/{name}")
    RoleOutVo getRoleByName(@PathVariable("name") String name);

    @PostMapping("/role/inner/user-bind-role")
    void userBindRole(@RequestParam("userId") String userId, @RequestParam("roleId") String roleId);

    @PostMapping("/role/add")
    RoleOutVo addRole(@RequestBody RoleOutVo role);
}
