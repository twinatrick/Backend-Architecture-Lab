package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Service.IBotConfigService;
import com.example.BackendArchitectureLab.Service.IExternalPlatformStatusService;
import com.example.BackendArchitectureLab.Vo.BotConfigVo;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.ExternalPlatformStatusVo;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Vo.Search.BotConfigSearchQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/external/config")
@ApiControllerTag(name = "Bot Config", description = "Bot 設定管理相關 API")
@RequiredArgsConstructor
@RequirePermission(layer = "BotConfig")
public class ConfigController {

    private final IBotConfigService botConfigService;
    private final IExternalPlatformStatusService externalPlatformStatusService;

    @PostMapping("/search")
    @RequirePermission("View")
    @ApiOperationBadRequest(summary = "分頁搜尋 Bot 設定", description = "依條件分頁搜尋 Bot 設定。")
    public ResponseType<PageResult<BotConfigVo>> search(@RequestBody BotConfigSearchQuery query) {
        return new ResponseType<>(botConfigService.searchBotConfigs(query));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequirePermission("View")
    @ApiOperationOk(summary = "分塊串流獲取 Bot 設定", description = "以 SSE 分塊串流方式獲取 Bot 設定清單。")
    public Flux<ServerSentEvent<List<BotConfigVo>>> streamBotConfigs(
            @RequestParam(defaultValue = "250") int chunkSize) {
        return botConfigService.streamBotConfigsChunked(chunkSize);
    }

    @GetMapping("/status")
    @RequirePermission("View")
    @ApiOperationOk(summary = "取得外部平台執行時狀態", description = "探查 Discord、LINE 與 AI 模型平台之連線與憑證配置狀態。")
    public ResponseType<ExternalPlatformStatusVo> getStatus() {
        return new ResponseType<>(externalPlatformStatusService.getPlatformStatus());
    }

    @GetMapping("/{id}")
    @RequirePermission("View")
    @ApiOperationOk(summary = "依 ID 取得 Bot 設定", description = "依 ID 取得單筆 Bot 設定。")
    public ResponseType<BotConfigVo> getById(@PathVariable UUID id) {
        return new ResponseType<>(botConfigService.findById(id));
    }

    @PostMapping
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "新增 Bot 設定", description = "建立新的 Bot 設定。")
    public ResponseType<BotConfigVo> create(@RequestBody BotConfigVo vo) {
        return new ResponseType<>(botConfigService.create(vo));
    }

    @PutMapping("/{id}")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "更新 Bot 設定", description = "依 ID 更新 Bot 設定。")
    public ResponseType<BotConfigVo> update(@PathVariable UUID id, @RequestBody BotConfigVo vo) {
        return new ResponseType<>(botConfigService.update(id, vo));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "刪除 Bot 設定", description = "依 ID 刪除 Bot 設定。")
    public ResponseType<Void> delete(@PathVariable UUID id) {
        botConfigService.delete(id);
        return new ResponseType<>(null);
    }
}
