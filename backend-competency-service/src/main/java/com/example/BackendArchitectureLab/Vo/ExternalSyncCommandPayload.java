package com.example.BackendArchitectureLab.Vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;
import java.util.UUID;

/**
 * ExternalSyncCommandPayload - 外部同步命令的持久化 payload：
 * 記錄要同步的目標綁定（memberSkillsMap）與重試/補償所需的 before-state，
 * 讓 ExternalSyncWorker 即使與 producer 分離執行也能自給自足重現同步/補償所需資料。
 */
@Getter
@Setter
@NoArgsConstructor
public class ExternalSyncCommandPayload {

    private Map<UUID, Map<UUID, UUID>> memberSkillsMap;

    private Map<String, Object> beforeState;
}