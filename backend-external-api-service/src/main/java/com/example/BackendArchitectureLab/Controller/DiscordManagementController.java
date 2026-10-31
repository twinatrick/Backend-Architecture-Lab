package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiBody;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiPath;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiQuery;
import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Service.IDiscordManagementService;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.DiscordGfMessageVo;
import com.example.BackendArchitectureLab.Vo.DiscordGfSessionVo;
import com.example.BackendArchitectureLab.Vo.DiscordSubscriptionVo;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Dto.Query.DiscordGfSessionSearchQuery;
import com.example.BackendArchitectureLab.Dto.Query.DiscordSubscriptionSearchQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/external/discord")
@ApiControllerTag(name = "Discord Management", description = "Discord 會話、訊息與訂閱管理相關 API")
@RequiredArgsConstructor
@RequirePermission(layer = "BotConfig")
public class DiscordManagementController {

    private final IDiscordManagementService discordManagementService;

    @PostMapping("/sessions/search")
    @RequirePermission("View")
    @ApiOperationOk(summary = "分頁搜尋 Discord 會話", description = "依條件分頁搜尋 Discord 女友對話會話清單。")
    public ResponseType<PageResult<DiscordGfSessionVo>> searchSessions(
            @ApiBody(description = "Discord 會話分頁搜尋條件") DiscordGfSessionSearchQuery query) {
        return new ResponseType<>(discordManagementService.searchSessions(query));
    }

    @GetMapping(value = "/sessions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequirePermission("View")
    @ApiOperationOk(summary = "串流取得 Discord 會話 (SSE)", description = "以 Server-Sent Events 分區塊串流取得 Discord 女友對話會話。")
    public Flux<ServerSentEvent<List<DiscordGfSessionVo>>> streamSessions(
            @ApiQuery(name = "chunkSize", defaultValue = "250", description = "單一串流區塊筆數", example = "250") int chunkSize) {
        return discordManagementService.streamSessionsChunked(chunkSize);
    }

    @GetMapping("/sessions/{id}")
    @RequirePermission("View")
    @ApiOperationOk(summary = "依 ID 取得 Discord 會話詳情", description = "取得單筆 Discord 女友對話會話設定與狀態。")
    public ResponseType<DiscordGfSessionVo> getSessionById(
            @ApiPath(name = "id", description = "Discord 會話 UUID", format = "uuid") UUID id) {
        return new ResponseType<>(discordManagementService.getSessionById(id));
    }

    @GetMapping("/sessions/{id}/messages")
    @RequirePermission("View")
    @ApiOperationOk(summary = "取得 Discord 會話歷史訊息", description = "依會話 ID 按需調閱 1:N 歷史訊息。")
    public ResponseType<List<DiscordGfMessageVo>> getSessionMessages(
            @ApiPath(name = "id", description = "Discord 會話 UUID", format = "uuid") UUID id) {
        return new ResponseType<>(discordManagementService.getSessionMessages(id));
    }

    @PatchMapping("/sessions/{id}/toggle")
    @RequirePermission("Edit")
    @ApiOperationOk(summary = "切換 Discord 會話啟用狀態", description = "切換指定會話的 active 狀態。")
    public ResponseType<DiscordGfSessionVo> toggleSessionActive(
            @ApiPath(name = "id", description = "Discord 會話 UUID", format = "uuid") UUID id) {
        return new ResponseType<>(discordManagementService.toggleSessionActive(id));
    }

    @DeleteMapping("/sessions/{id}")
    @RequirePermission("Edit")
    @ApiOperationOk(summary = "刪除 Discord 會話", description = "依 ID 刪除會話並級聯清理其所有歷史訊息。")
    public ResponseType<Void> deleteSession(
            @ApiPath(name = "id", description = "Discord 會話 UUID", format = "uuid") UUID id) {
        discordManagementService.deleteSession(id);
        return new ResponseType<>(null);
    }

    @PostMapping("/subscriptions/search")
    @RequirePermission("View")
    @ApiOperationOk(summary = "分頁搜尋 Discord 頻道訂閱", description = "依條件分頁搜尋 Discord 頻道訂閱紀錄。")
    public ResponseType<PageResult<DiscordSubscriptionVo>> searchSubscriptions(
            @ApiBody(description = "Discord 頻道訂閱分頁搜尋條件") DiscordSubscriptionSearchQuery query) {
        return new ResponseType<>(discordManagementService.searchSubscriptions(query));
    }

    @GetMapping(value = "/subscriptions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequirePermission("View")
    @ApiOperationOk(summary = "串流取得 Discord 頻道訂閱 (SSE)", description = "以 Server-Sent Events 分區塊串流取得 Discord 頻道訂閱清單。")
    public Flux<ServerSentEvent<List<DiscordSubscriptionVo>>> streamSubscriptions(
            @ApiQuery(name = "chunkSize", defaultValue = "250", description = "單一串流區塊筆數", example = "250") int chunkSize) {
        return discordManagementService.streamSubscriptionsChunked(chunkSize);
    }

    @DeleteMapping("/subscriptions/{id}")
    @RequirePermission("Edit")
    @ApiOperationOk(summary = "刪除 Discord 頻道訂閱", description = "依 ID 刪除頻道訂閱紀錄。")
    public ResponseType<Void> deleteSubscription(
            @ApiPath(name = "id", description = "Discord 頻道訂閱 UUID", format = "uuid") UUID id) {
        discordManagementService.deleteSubscription(id);
        return new ResponseType<>(null);
    }
}
