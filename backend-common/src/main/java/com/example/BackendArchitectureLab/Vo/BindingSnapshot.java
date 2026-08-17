package com.example.BackendArchitectureLab.Vo;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * BindingSnapshot - 補償還原所需的歷史綁定快照明細（persisted beforeState 的型別化表徵）。
 * <p>
 * 自 persisted payload（或內網 Feign 請求 body）反序列化後，於還原流程的
 * 破壞性操作（DELETE）之前完成型別與內容驗證，避免中途才發生 runtime exception。
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class BindingSnapshot {

    private UUID userId;

    private UUID skillId;

    private UUID levelId;

    public BindingSnapshot(UUID userId, UUID skillId, UUID levelId) {
        this.userId = userId;
        this.skillId = skillId;
        this.levelId = levelId;
    }
}