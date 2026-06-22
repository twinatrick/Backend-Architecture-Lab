package com.example.BackendArchitectureLab.Service.impl;

import com.example.BackendArchitectureLab.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Vo.RoleOutVo;
import com.example.BackendArchitectureLab.Vo.UserVo;
import com.example.BackendArchitectureLab.Service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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
        allFunctionList.add(new ArrayList<>( List.of("System", "User", "View")));
        allFunctionList.add(new ArrayList<>( List.of("System", "RolePermission", "View")));
        allFunctionList.add(new ArrayList<>( List.of("DataView", "AquarkData", "View")));
        allFunctionList.add(new ArrayList<>( List.of("DataView", "AquarkDataAvg", "View")));
        allFunctionList.add(new ArrayList<>( List.of("System", "LimitSetting", "View")));
        allFunctionList.add(new ArrayList<>( List.of("System", "SkillManagement", "View")));
        allFunctionList.add(new ArrayList<>( List.of("System", "ProjectManagement", "View")));
        allFunctionList.add(new ArrayList<>( List.of("System", "ProjectManagement", "Edit")));
        allFunctionList.add(new ArrayList<>( List.of("System", "ProjectManagement", "EditAll")));

        allFunctionList.add(new ArrayList<>( List.of("System", "Company", "View")));
        allFunctionList.add(new ArrayList<>( List.of("System", "Company", "Edit")));
        allFunctionList.add(new ArrayList<>( List.of("System", "JobPosting", "View")));
        allFunctionList.add(new ArrayList<>( List.of("System", "JobPosting", "Edit")));
        allFunctionList.add(new ArrayList<>( List.of("System", "JobPosting", "Scrape")));
        allFunctionList.add(new ArrayList<>( List.of("System", "UserJobLink", "View")));
        allFunctionList.add(new ArrayList<>( List.of("System", "UserJobLink", "Edit")));
        allFunctionList.add(new ArrayList<>( List.of("System", "User", "Edit")));
        allFunctionList.add(new ArrayList<>( List.of("System", "Function", "View")));
        allFunctionList.add(new ArrayList<>( List.of("System", "Function", "Edit")));
        allFunctionList.add(new ArrayList<>( List.of("System", "Role", "View")));
        allFunctionList.add(new ArrayList<>( List.of("System", "Role", "Edit")));

        allFunctionList.add(new ArrayList<>( List.of("System", "Skill", "View")));
        allFunctionList.add(new ArrayList<>( List.of("System", "Skill", "Edit")));
        allFunctionList.add(new ArrayList<>( List.of("System", "SkillLevel", "View")));
        allFunctionList.add(new ArrayList<>( List.of("System", "SkillLevel", "Edit")));
        allFunctionList.add(new ArrayList<>( List.of("System", "PersonalSkill", "Edit")));
        allFunctionList.add(new ArrayList<>( List.of("System", "AlertLimit", "View")));
        allFunctionList.add(new ArrayList<>( List.of("System", "AlertLimit", "Edit")));
        allFunctionList.add(new ArrayList<>( List.of("System", "AquarkData", "View")));
        allFunctionList.add(new ArrayList<>( List.of("System", "Project", "View")));
        allFunctionList.add(new ArrayList<>( List.of("System", "Project", "Edit")));
        allFunctionList.add(new ArrayList<>( List.of("System", "Project", "PersonalEdit")));

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
                    .collect(java.util.stream.Collectors.toSet());
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
