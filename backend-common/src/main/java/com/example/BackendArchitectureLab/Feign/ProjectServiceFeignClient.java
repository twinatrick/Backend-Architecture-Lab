package com.example.BackendArchitectureLab.Feign;

import com.example.BackendArchitectureLab.Dto.Vo.ProjectVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@FeignClient(name = "project-skill-service", contextId = "projectServiceClient")
public interface ProjectServiceFeignClient {

    @GetMapping("/project/inner/{id}")
    ProjectVo getProjectById(@PathVariable("id") UUID id);

    @PostMapping("/project/inner/rebind-skills")
    void rebindProjectSkills(@RequestParam("projectId") UUID projectId,
                             @RequestBody Map<UUID, UUID> skillLevelMapping);

    @GetMapping("/project/inner/project-skill/exists-by-level/{levelId}")
    boolean existsProjectSkillByLevelId(@PathVariable("levelId") UUID levelId);

    @PostMapping("/project/inner/project-skill/delete-by-skill")
    void deleteProjectSkillsBySkillId(@RequestParam("skillId") UUID skillId);

    @GetMapping("/project/inner/user-project/exists")
    boolean existsUserProject(@RequestParam("userId") UUID userId,
                              @RequestParam("projectId") UUID projectId);

    @GetMapping("/project/inner/user-project/ids/{userId}")
    List<UUID> getUserProjectIds(@PathVariable("userId") UUID userId);

    @PostMapping("/project/inner/user-project/save")
    void saveUserProject(@RequestParam("userId") UUID userId,
                         @RequestParam("projectId") UUID projectId);

    @PostMapping("/project/inner/user-project/delete")
    void deleteUserProject(@RequestParam("userId") UUID userId,
                           @RequestParam("projectId") UUID projectId);
}
