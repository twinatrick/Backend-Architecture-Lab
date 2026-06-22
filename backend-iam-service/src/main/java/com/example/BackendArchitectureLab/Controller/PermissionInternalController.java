package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Vo.RoleOutVo;
import com.example.BackendArchitectureLab.Vo.UserVo;
import com.example.BackendArchitectureLab.Service.IFunctionService;
import com.example.BackendArchitectureLab.Service.IRoleService;
import com.example.BackendArchitectureLab.Service.IUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/role/inner")
public class PermissionInternalController {
    private static final Logger log = LoggerFactory.getLogger(PermissionInternalController.class);

    @Autowired
    private IRoleService roleService;
    @Autowired
    private IUserService userService;
    @Autowired
    private IFunctionService functionService;

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

        FunctionVo func1 = functionService.getFunctionByName(one);
        if (func1 == null) {
            log.warn("Permission validation failed: function level1 '{}' not found for user={}", one, email);
            return false;
        }
        FunctionVo func2 = functionService.getFunctionByNameAndParent(two, func1.getId());
        if (func2 == null) {
            log.warn("Permission validation failed: function level2 '{}' not found for user={}", two, email);
            return false;
        }
        FunctionVo func3 = functionService.getFunctionByNameAndParent(three, func2.getId());
        if (func3 == null) {
            log.warn("Permission validation failed: function level3 '{}' not found for user={}", three, email);
            return false;
        }

        String requiredFunctionId = func3.getId();

        UserVo user = userService.getOnlyUserByEmail(email);
        if (user == null) {
            log.warn("Permission validation failed: user not found for email={}", email);
            return false;
        }

        boolean hasPermission = user.getPermissions() != null && user.getPermissions().stream()
                .map(FunctionVo::getId)
                .filter(Objects::nonNull)
                .anyMatch(id -> id.equals(requiredFunctionId));

        if (!hasPermission) {
            log.warn("Permission denied: user={}, path={}/{}/{}, requiredFunctionId={}",
                    email, one, two, three, requiredFunctionId);
        } else {
            log.debug("Permission granted: user={}, path={}/{}/{}", email, one, two, three);
        }

        return hasPermission;
    }
}
