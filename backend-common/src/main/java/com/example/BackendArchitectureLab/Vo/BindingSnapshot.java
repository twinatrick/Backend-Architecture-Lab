package com.example.BackendArchitectureLab.Vo;

import java.util.UUID;

/**
 * BindingSnapshot - 補償還原所需的歷史綁定快照明細（persisted beforeState 的型別化表徵）。
 * <p>
 * 自 persisted payload（或內網 Feign 請求 body）反序列化後，於還原流程的
 * 破壞性操作（DELETE）之前完成型別與內容驗證，避免中途才發生 runtime exception。
 * <p>
 * 註：保留 {@code getUserId()}、{@code getSkillId()}、{@code getLevelId()} 係為確保與
 * 既有 JavaBean 反射規範、舊版序列化工具及 Spring EL 表達式完全相容；
 * 內部業務邏輯中建議優先使用標準 Record Component Accessors（{@link #userId()} 等）。
 */
public record BindingSnapshot(
        UUID userId,
        UUID skillId,
        UUID levelId
) {
    /**
     * 相容 JavaBean 命名慣例之 Getter。
     */
    public UUID getUserId() {
        return userId;
    }

    /**
     * 相容 JavaBean 命名慣例之 Getter。
     */
    public UUID getSkillId() {
        return skillId;
    }

    /**
     * 相容 JavaBean 命名慣例之 Getter。
     */
    public UUID getLevelId() {
        return levelId;
    }
}
