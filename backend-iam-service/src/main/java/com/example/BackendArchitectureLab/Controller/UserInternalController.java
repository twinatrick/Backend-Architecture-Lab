package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Dto.Vo.UserVo;
import com.example.BackendArchitectureLab.Service.IUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users/inner")
public class UserInternalController {
    @Autowired
    private IUserService userService;

    @GetMapping("/{id}")
    public UserVo getUserById(@PathVariable UUID id) {
        return userService.getUserById(id.toString());
    }

    @PostMapping("/by-email")
    public UserVo getUserByEmail(@RequestBody String email) {
        return userService.getOnlyUserByEmail(email);
    }

    @PostMapping("/rebind-projects")
    public void rebindUserProjects(@RequestParam("userId") UUID userId,
                                   @RequestBody List<UUID> projectIds) {
        userService.rebindUserProjects(userId, projectIds);
    }

    @GetMapping("/exists/{id}")
    public boolean existsUserById(@PathVariable UUID id) {
        try {
            userService.getUserById(id.toString());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @GetMapping("/by-email-exists")
    public boolean existsUserByEmail(@RequestParam String email) {
        try {
            userService.getOnlyUserByEmail(email);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
