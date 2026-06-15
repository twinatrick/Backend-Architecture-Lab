package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Dto.Vo.ProjectVo;
import com.example.BackendArchitectureLab.Service.IProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/project/inner")
public class ProjectInternalController {
    @Autowired
    private IProjectService projectService;

    @GetMapping("/{id}")
    public ProjectVo getProjectById(@PathVariable UUID id) {
        return projectService.getProjectById(id);
    }

    @PostMapping("/rebind-skills")
    public void rebindProjectSkills(@RequestParam("projectId") UUID projectId,
                                    @RequestBody Map<UUID, UUID> skillLevelMapping) {
        projectService.rebindProjectSkills(projectId, skillLevelMapping);
    }

    @GetMapping("/project-skill/exists-by-level/{levelId}")
    public boolean existsProjectSkillByLevelId(@PathVariable UUID levelId) {
        return projectService.existsProjectSkillByLevelId(levelId);
    }

    @PostMapping("/project-skill/delete-by-skill")
    public void deleteProjectSkillsBySkillId(@RequestParam("skillId") UUID skillId) {
        projectService.deleteProjectSkillsBySkillId(skillId);
    }

    @GetMapping("/user-project/exists")
    public boolean existsUserProject(@RequestParam("userId") UUID userId,
                                     @RequestParam("projectId") UUID projectId) {
        return projectService.existsUserProject(userId, projectId);
    }

    @GetMapping("/user-project/ids/{userId}")
    public List<UUID> getUserProjectIds(@PathVariable UUID userId) {
        return projectService.getUserProjectIds(userId);
    }

    @PostMapping("/user-project/save")
    public void saveUserProject(@RequestParam("userId") UUID userId,
                                @RequestParam("projectId") UUID projectId) {
        projectService.saveUserProject(userId, projectId);
    }

    @PostMapping("/user-project/delete")
    public void deleteUserProject(@RequestParam("userId") UUID userId,
                                  @RequestParam("projectId") UUID projectId) {
        projectService.deleteUserProject(userId, projectId);
    }
}
