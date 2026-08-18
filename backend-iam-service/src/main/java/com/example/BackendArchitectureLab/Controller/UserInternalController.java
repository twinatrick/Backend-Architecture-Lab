package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Vo.UserVo;
import com.example.BackendArchitectureLab.Exception.AppException;
import com.example.BackendArchitectureLab.Service.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users/inner")
@RequiredArgsConstructor
public class UserInternalController {
    private final IUserService userService;

    @GetMapping("/{id}")
    public UserVo getUserById(@PathVariable UUID id) {
        return userService.getUserById(id.toString());
    }

    @PostMapping("/by-email")
    public UserVo getUserByEmail(@RequestParam("email") String email) {
        return userService.getOnlyUserByEmail(email);
    }

    @GetMapping("/exists/{id}")
    public boolean existsUserById(@PathVariable UUID id) {
        try {
            userService.getUserById(id.toString());
            return true;
        } catch (AppException e) {
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
