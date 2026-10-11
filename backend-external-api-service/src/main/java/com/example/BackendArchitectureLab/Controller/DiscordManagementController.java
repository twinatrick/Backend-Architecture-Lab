package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Service.IDiscordManagementService;
import com.example.BackendArchitectureLab.Vo.DiscordGfMessageVo;
import com.example.BackendArchitectureLab.Vo.DiscordGfSessionVo;
import com.example.BackendArchitectureLab.Vo.DiscordSubscriptionVo;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/external/discord")
@ApiControllerTag(name = "Discord Management", description = "Discord 會話、訊息與訂閱管理相關 API")
@RequiredArgsConstructor
@RequirePermission(layer = "BotConfig")
public class DiscordManagementController {

    private final IDiscordManagementService discordManagementService;

    @GetMapping("/sessions")
    @RequirePermission("View")
    @ApiOperationOk(summary = "取得 Discord 會話清單", description = "可選依伺服器 ID (guildId) 與啟用狀態 (active) 篩選 Discord 會話。")
    public ResponseType<List<DiscordGfSessionVo>> getSessions(
            @RequestParam(required = false) String guildId,
            @RequestParam(required = false) Boolean active) {
        return new ResponseType<>(discordManagementService.getSessions(guildId, active));
    }

    @GetMapping("/sessions/{id}")
    @RequirePermission("View")
    @ApiOperationOk(summary = "依 ID 取得 Discord 會話詳情", description = "取得單筆 Discord 女友對話會話設定與狀態。")
    public ResponseType<DiscordGfSessionVo> getSessionById(@PathVariable UUID id) {
        return new ResponseType<>(discordManagementService.getSessionById(id));
    }

    @GetMapping("/sessions/{id}/messages")
    @RequirePermission("View")
    @ApiOperationOk(summary = "取得 Discord 會話歷史訊息", description = "依會話 ID 按需調閱 1:N 歷史訊息。")
    public ResponseType<List<DiscordGfMessageVo>> getSessionMessages(@PathVariable UUID id) {
        return new ResponseType<>(discordManagementService.getSessionMessages(id));
    }

    @PatchMapping("/sessions/{id}/toggle")
    @RequirePermission("Edit")
    @ApiOperationOk(summary = "切換 Discord 會話啟用狀態", description = "切換指定會話的 active 狀態。")
    public ResponseType<DiscordGfSessionVo> toggleSessionActive(@PathVariable UUID id) {
        return new ResponseType<>(discordManagementService.toggleSessionActive(id));
    }

    @DeleteMapping("/sessions/{id}")
    @RequirePermission("Edit")
    @ApiOperationOk(summary = "刪除 Discord 會話", description = "依 ID 刪除會話並級聯清理其所有歷史訊息。")
    public ResponseType<Void> deleteSession(@PathVariable UUID id) {
        discordManagementService.deleteSession(id);
        return new ResponseType<>(null);
    }

    @GetMapping("/subscriptions")
    @RequirePermission("View")
    @ApiOperationOk(summary = "取得 Discord 頻道訂閱清單", description = "可選依伺服器 ID (guildId) 篩選語音日記等頻道訂閱。")
    public ResponseType<List<DiscordSubscriptionVo>> getSubscriptions(
            @RequestParam(required = false) String guildId) {
        return new ResponseType<>(discordManagementService.getSubscriptions(guildId));
    }

    @DeleteMapping("/subscriptions/{id}")
    @RequirePermission("Edit")
    @ApiOperationOk(summary = "刪除 Discord 頻道訂閱", description = "依 ID 刪除頻道訂閱紀錄。")
    public ResponseType<Void> deleteSubscription(@PathVariable UUID id) {
        discordManagementService.deleteSubscription(id);
        return new ResponseType<>(null);
    }
}
