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
 * 本服務由 {@code Timer/ExternalSyncWorker} 依 durable command（external_sync_command）
 * 呼叫：命令與業務交易同 commit，消除「本地 commit 後 JVM crash 導致外部同步永不執行」
 * 的 crash window；失敗由 worker 依退避重試，重試耗盡（DEAD）才觸發補償閉環。
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