package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Service.IExternalSyncService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * ExternalSyncServiceImpl - 專案成員技能綁定的外部系統同步實作。
 * <p>
 * 目前為「未啟用」的 infrastructure adapter（external-sync.enabled 預設為 false）：
 * 尚未接線真實外部系統，因此啟用時（enabled=true 且 base-url 已設定）呼叫
 * {@link #syncProjectMemberSkills} 會明確拋出例外以觸發補償流程，而非靜默成功。
 * 本服務僅在本地事務 commit 後由 ProjectUserBindingService 呼叫，失敗時拋出例外
 * 以觸發補償流程（不參與資料庫 rollback）。
 */
@Slf4j
@Service
public class ExternalSyncServiceImpl implements IExternalSyncService {

    @Value("${external-sync.enabled:false}")
    private boolean syncEnabled;

    @Value("${external-sync.base-url:}")
    private String baseUrl;

    @PostConstruct
    void validateConfiguration() {
        if (syncEnabled && (baseUrl == null || baseUrl.isBlank())) {
            throw new IllegalStateException(
                    "external-sync is enabled but base-url is not configured; failing fast to avoid silent skip");
        }
    }

    @Override
    public void syncProjectMemberSkills(UUID projectId, Map<UUID, Map<UUID, UUID>> memberSkillsMap) {
        log.info("External sync project member skills: projectId={}, memberCount={}, syncEnabled={}",
                projectId, memberSkillsMap.size(), syncEnabled);

        if (!syncEnabled) {
            log.debug("External sync disabled by configuration, skip actual call: projectId={}", projectId);
            return;
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("External sync base-url not configured: projectId=" + projectId);
        }

        // 尚未接線真實外部系統（YAGNI）：明確失敗而非靜默成功，
        // 讓呼叫端 ProjectUserBindingService 觸發補償流程。
        throw new UnsupportedOperationException(
                "External sync adapter is not implemented yet; base-url=" + baseUrl + ", projectId=" + projectId);
    }
}