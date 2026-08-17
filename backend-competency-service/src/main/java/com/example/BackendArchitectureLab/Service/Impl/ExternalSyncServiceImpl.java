package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Service.IExternalSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * ExternalSyncServiceImpl - 專案成員技能綁定的外部系統同步實作。
 * <p>
 * 目前以組態化骨架提供：透過 {@code external-sync.enabled} 控制是否實際對外呼叫，
 * 未來接線真實外部系統時，可在此以 {@code external-sync.base-url} 為基底建立 HTTP 呼叫。
 * 本服務僅在本地事務 commit 後由 ProjectUserBindingService 呼叫，失敗時拋出例外
 * 以觸發補償流程（不參與資料庫 rollback）。
 */
@Slf4j
@Service
public class ExternalSyncServiceImpl implements IExternalSyncService {

    @Value("${external-sync.enabled:true}")
    private boolean syncEnabled;

    @Value("${external-sync.base-url:}")
    private String baseUrl;

    @Override
    public void syncProjectMemberSkills(UUID projectId, Map<UUID, Map<UUID, UUID>> memberSkillsMap) {
        log.info("External sync project member skills: projectId={}, memberCount={}, syncEnabled={}",
                projectId, memberSkillsMap.size(), syncEnabled);

        if (!syncEnabled) {
            log.debug("External sync disabled by configuration, skip actual call: projectId={}", projectId);
            return;
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            log.debug("External sync base-url not configured, skip actual call: projectId={}", projectId);
            return;
        }

        // TODO: 接線真實外部系統（如以 baseUrl 組裝 REST 呼叫並檢查 response），
        //  失敗時拋出例外，由呼叫端 ProjectUserBindingService 觸發補償。
        log.info("External sync call placeholder (base-url={}): projectId={}", baseUrl, projectId);
    }
}