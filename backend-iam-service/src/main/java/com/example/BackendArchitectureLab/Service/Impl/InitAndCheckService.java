package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Vo.RoleOutVo;
import com.example.BackendArchitectureLab.Vo.UserVo;
import com.example.BackendArchitectureLab.Service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class InitAndCheckService implements IInitAndCheckService {
    @Autowired
    private IRoleService roleService;

    @Autowired
    private IUserService userService;

    @Autowired
    private IFunctionService functionService;

    @Override
    public void initAndCheck() {
        checkRole();

        checkFunctionBindDefaultRole();
    }

    @Override
    public void checkRole() {
        List<RoleOutVo> roleList = roleService.getRole();
        RoleOutVo role = new RoleOutVo();
        if (!roleList.isEmpty()) {
            if (roleService.getRoleByName("admin") == null) {
                role.setName("admin");
                roleService.addRole(role);
            }
            if (roleService.getRoleByName("user") == null) {
                role = new RoleOutVo();
                role.setName("user");
                roleService.addRole(role);
            }
        }else {
            role.setName("admin");
            roleService.addRole(role);
            role = new RoleOutVo();
            role.setName("user");
            roleService.addRole(role);
        }
        var user = userService.getUserByEmail("admin").stream().findFirst().orElseGet(() -> {
            userService.createUser(new UserVo() {{
                setEmail("admin");
                setPassword("admin");
            }});
            return userService.getUserByEmail("admin").getFirst();
        });
        role = roleService.getRoleByName("admin");
        if (role != null) {
            roleService.roleBindUser(role.getId().toString(), List.of(user.getId()));
        }


    }

    @Override
    public boolean checkIsExist(String oneLayer, String twoLayer, String threeLayer) {
        FunctionVo one= functionService.getFunctionByName(oneLayer);
        if (one == null) {
            return false;
        }
        FunctionVo two = functionService.getFunctionByNameAndParent(twoLayer, one.getId());

        if (two == null) {
            return false;
        }
        FunctionVo three = functionService.getFunctionByNameAndParent(threeLayer, two.getId());
        if (three == null) {
            return false;
        }
        return true;
    }

    @Override
    public void checkFunctionBindDefaultRole() {
        List<List<String>> allFunctionList = new ArrayList<>();
        List.of(
                // Iam
                List.of("Iam", "User", "View"),
                List.of("Iam", "User", "Create"),
                List.of("Iam", "User", "Edit"),
                List.of("Iam", "Role", "View"),
                List.of("Iam", "Role", "Edit"),
                List.of("Iam", "Function", "View"),
                List.of("Iam", "Function", "Edit"),
                // Competency
                List.of("Competency", "Skill", "View"),
                List.of("Competency", "Skill", "Edit"),
                List.of("Competency", "SkillLevel", "View"),
                List.of("Competency", "SkillLevel", "Edit"),
                List.of("Competency", "PersonalSkill", "Edit"),
                List.of("Competency", "PersonalProject", "View"),
                List.of("Competency", "PersonalProject", "Edit"),
                List.of("Competency", "ProjectManagement", "View"),
                List.of("Competency", "ProjectManagement", "Edit"),
                List.of("Competency", "Project", "View"),
                List.of("Competency", "Project", "Edit"),
                // Job
                List.of("Job", "Company", "View"),
                List.of("Job", "Company", "Edit"),
                List.of("Job", "JobPosting", "View"),
                List.of("Job", "JobPosting", "Edit"),
                List.of("Job", "JobPosting", "Scrape"),
                List.of("Job", "UserJobLink", "View"),
                List.of("Job", "UserJobLink", "Edit"),
                // Alert
                List.of("Alert", "AquarkData", "View"),
                List.of("Alert", "AlertLimit", "View"),
                List.of("Alert", "AlertLimit", "Edit"),
                // ExternalApi
                List.of("ExternalApi", "VoiceUpload", "View"),
                List.of("ExternalApi", "VoiceUpload", "Edit")
        ).forEach(entry -> allFunctionList.add(new ArrayList<>(entry)));

        for (List<String> functionListStr : allFunctionList) {
            if (!checkIsExist(functionListStr.get(0), functionListStr.get(1), functionListStr.get(2))) {
                insertFunctionByList(functionListStr,"");
            }
        }


        List<FunctionVo> functionList = functionService.getFunction();
        RoleOutVo role = roleService.getRoleByName("admin");
        if (role != null) {
            var parentIds = functionList.stream()
                    .map(FunctionVo::getParent)
                    .filter(p -> p != null && !p.isBlank())
                    .collect(Collectors.toSet());
            List<String> leafFunctionIds = functionList.stream()
                    .map(FunctionVo::getId)
                    .filter(id -> !parentIds.contains(id))
                    .toList();
            roleService.roleBindFunction(role.getId().toString(), leafFunctionIds);
        }
    }
    @Override
    public void insertFunctionByList(List<String> functionList , String parent) {
        if (functionList.isEmpty()) {
            return;
        }
        FunctionVo sameFunction = functionService.getFunctionByNameAndParent(functionList.getFirst(), parent);
        if (sameFunction != null) {
            insertFunctionByList(functionList.subList(1, functionList.size()), sameFunction.getId());
        }else {
            FunctionVo f = new FunctionVo();
            f.setName(functionList.getFirst());
            f.setParent(parent);
            f = functionService.addFunction(f);
            insertFunctionByList(functionList.subList(1, functionList.size()), f.getId());
        }

    }

}
