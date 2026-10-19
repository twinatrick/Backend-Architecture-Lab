package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Aop.LocalPermissionValidator;
import com.example.BackendArchitectureLab.Vo.RoleVo;
import com.example.BackendArchitectureLab.Service.IRoleService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/role/inner")
@RequiredArgsConstructor
public class PermissionInternalController {
    private static final Logger log = LoggerFactory.getLogger(PermissionInternalController.class);

    private final IRoleService roleService;
    private final LocalPermissionValidator localPermissionValidator;

    @GetMapping("/all")
    public List<RoleVo> getAllRoles() {
        return roleService.getRole();
    }

    @GetMapping("/by-name/{name}")
    public RoleVo getRoleByName(@PathVariable String name) {
        return roleService.getRoleByName(name);
    }

    @PostMapping("/user-bind-role")
    public void userBindRole(@RequestParam String userId, @RequestParam String roleId) {
        roleService.userBindRole(userId, List.of(roleId));
    }

    @PostMapping("/validate")
    public boolean validatePermission(@RequestParam String email,
                                      @RequestParam String one,
                                      @RequestParam String two,
                                      @RequestParam String three) {
        log.debug("Validating permission: email={}, path={}/{}/{}", email, one, two, three);
        boolean hasPermission = localPermissionValidator.validate(email, one, two, three);
        if (!hasPermission) {
            log.warn("Permission denied: user={}, path={}/{}/{}", email, one, two, three);
        } else {
            log.debug("Permission granted: user={}, path={}/{}/{}", email, one, two, three);
        }
        return hasPermission;
    }

    @GetMapping("/user-permissions")
    public Set<String> getUserPermissions(@RequestParam String email) {
        log.debug("Getting user permissions: email={}", email);
        return localPermissionValidator.getUserPermissions(email);
    }
}
