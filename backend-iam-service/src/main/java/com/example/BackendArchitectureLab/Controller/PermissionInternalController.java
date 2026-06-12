package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Dto.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Dto.Vo.RoleOutVo;
import com.example.BackendArchitectureLab.Dto.Vo.UserVo;
import com.example.BackendArchitectureLab.Service.IFunctionService;
import com.example.BackendArchitectureLab.Service.IRoleService;
import com.example.BackendArchitectureLab.Service.IUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/role/inner")
public class PermissionInternalController {
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
        FunctionVo func1 = functionService.getFunctionByName(one);
        if (func1 == null) return false;
        FunctionVo func2 = functionService.getFunctionByNameAndParent(two, func1.getId());
        if (func2 == null) return false;
        FunctionVo func3 = functionService.getFunctionByNameAndParent(three, func2.getId());
        if (func3 == null) return false;

        String requiredFunctionId = func3.getId();

        UserVo user = userService.getOnlyUserByEmail(email);
        if (user == null) return false;

        return user.getPermissions() != null && user.getPermissions().stream()
                .map(FunctionVo::getId)
                .filter(Objects::nonNull)
                .anyMatch(id -> id.equals(requiredFunctionId));
    }
}
