package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Aop.LocalPermissionValidator;
import com.example.BackendArchitectureLab.Vo.RoleOutVo;
import com.example.BackendArchitectureLab.Service.IRoleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/role/inner")
public class PermissionInternalController {
    private static final Logger log = LoggerFactory.getLogger(PermissionInternalController.class);

    @Autowired
    private IRoleService roleService;
    @Autowired
    private LocalPermissionValidator localPermissionValidator;

    @GetMapping("/all")
    public List<RoleOutVo> getAllRoles() {
        return roleService.getRole();
    }

    @GetMapping("/by-name/{name}")
    public RoleOutVo getRoleByName(@PathVariable String name) {
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
}
