package com.example.BackendArchitectureLab.Feign;

import com.example.BackendArchitectureLab.Dto.Vo.SkillLevelVo;
import com.example.BackendArchitectureLab.Dto.Vo.SkillVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;
import java.util.UUID;

@FeignClient(name = "project-skill-service", contextId = "skillServiceClient")
public interface SkillServiceFeignClient {

    @GetMapping("/skill/inner/{id}")
    SkillVo getSkillById(@PathVariable("id") Long id);

    @PostMapping("/skill/inner/bind-project")
    void bindProjectSkill(@RequestParam("projectId") String projectId,
                          @RequestParam("skillId") String skillId,
                          @RequestParam("skillLevelId") String skillLevelId);

    @PostMapping("/skill/inner/rebind-user-skills")
    void rebindUserSkills(@RequestParam("userId") UUID userId,
                          @RequestBody Map<UUID, UUID> skillLevelMap);

    @GetMapping("/skill/inner/level/{id}")
    SkillLevelVo getSkillLevelById(@PathVariable("id") Long id);
}
