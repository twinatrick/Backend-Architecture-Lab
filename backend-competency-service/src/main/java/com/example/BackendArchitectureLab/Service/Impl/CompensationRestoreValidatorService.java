package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.ISkillDataAccess;
import com.example.BackendArchitectureLab.DataAccess.ISkillLevelDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectDataAccess;
import com.example.BackendArchitectureLab.DataAccess.IUserProjectSkillDataAccess;
import com.example.BackendArchitectureLab.Entity.Skill;
import com.example.BackendArchitectureLab.Entity.SkillLevel;
import com.example.BackendArchitectureLab.Entity.UserProject;
import com.example.BackendArchitectureLab.Entity.UserProjectSkill;
import com.example.BackendArchitectureLab.Service.ICompensationRestoreValidatorService;
import com.example.BackendArchitectureLab.Vo.BindingSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CompensationRestoreValidatorService - 補償還原的綁定驗證與冪等比對實作（M-02 拆分）。
 * 不含交易或資料變更，僅提供唯讀驗證，供 CompensationRestoreService 於破壞性操作前呼叫。
 */
@Service
@RequiredArgsConstructor
public class CompensationRestoreValidatorService implements ICompensationRestoreValidatorService {

    public static final int MAX_BINDINGS = 1000;

    private final IUserProjectSkillDataAccess userProjectSkillDataAccess;
    private final IUserProjectDataAccess userProjectDataAccess;
    private final ISkillDataAccess skillDataAccess;
    private final ISkillLevelDataAccess skillLevelDataAccess;

    @Override
    public boolean isBindingsAlreadyRestored(UUID projectId, List<BindingSnapshot> bindings) {
        List<UserProjectSkill> current = userProjectSkillDataAccess.findByProjectId(projectId);
        Set<List<UUID>> currentSet = new HashSet<>();
        if (current != null) {
            for (UserProjectSkill row : current) {
                currentSet.add(List.of(
                        row.getUserId(), row.getSkill().getId(), row.getSkillLevel().getId()));
            }
        }
        Set<List<UUID>> targetSet = new HashSet<>();
        if (bindings != null) {
            for (BindingSnapshot b : bindings) {
                targetSet.add(List.of(b.getUserId(), b.getSkillId(), b.getLevelId()));
            }
        }
        return currentSet.equals(targetSet);
    }

    @Override
    public void validateBindingsForRestore(UUID projectId, List<BindingSnapshot> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return;
        }

        if (bindings.size() > MAX_BINDINGS) {
            throw new IllegalArgumentException(
                    "Bindings count " + bindings.size() + " exceeds maximum allowed limit of " + MAX_BINDINGS);
        }

        // 1. 記憶體去重校驗：同一成員不可綁定重複技能
        Set<String> seenUserSkill = new HashSet<>();
        for (BindingSnapshot binding : bindings) {
            if (binding instanceof BindingSnapshot(UUID userId, UUID skillId, UUID levelId)) {
                if (userId == null || skillId == null || levelId == null) {
                    throw new IllegalArgumentException(
                            "Invalid binding snapshot: userId/skillId/levelId must not be null, got " + binding);
                }

                String userSkillKey = userId + ":" + skillId;
                if (!seenUserSkill.add(userSkillKey)) {
                    throw new IllegalArgumentException(
                            "Duplicate binding snapshot detected for user " + userId + " and skill " + skillId);
                }
            }
        }

        // 2. 批次載入專案成員，避免 N 次 DB roundtrip
        List<UserProject> projectMembers = userProjectDataAccess.findByProjectId(projectId);
        Set<UUID> memberUserIds = (projectMembers != null)
                ? projectMembers.stream().map(UserProject::getUserId).collect(Collectors.toSet())
                : Set.of();

        // 3. 驗證技能與等級是否存在且匹配
        for (BindingSnapshot binding : bindings) {
            if (binding instanceof BindingSnapshot(UUID userId, UUID skillId, UUID levelId)) {
                if (!memberUserIds.contains(userId)) {
                    throw new IllegalArgumentException(
                            "User " + userId + " is not a member of project " + projectId);
                }

                Skill skill = skillDataAccess.findById(skillId)
                        .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillId));

                SkillLevel skillLevel = skillLevelDataAccess.findById(levelId)
                        .orElseThrow(() -> new IllegalArgumentException("Skill level not found: " + levelId));

                if (!skillLevel.getSkill().getId().equals(skillId)) {
                    throw new IllegalArgumentException(
                            "Skill level " + levelId + " does not belong to skill " + skillId);
                }
            }
        }
    }
}
