package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Vo.BotConfigVo;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Service.IBotConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/external/config")
@ApiControllerTag(name = "Bot Config", description = "Bot 設定管理相關 API")
@RequiredArgsConstructor
public class ConfigController {

    private final IBotConfigService botConfigService;

    @GetMapping
    @ApiOperationOk(summary = "取得所有 Bot 設定", description = "回傳所有 Bot 設定。")
    public ResponseType<List<BotConfigVo>> getAll() {
        return new ResponseType<>(botConfigService.findAll());
    }

    @GetMapping("/{id}")
    @ApiOperationOk(summary = "依 ID 取得 Bot 設定", description = "依 ID 取得單筆 Bot 設定。")
    public ResponseType<BotConfigVo> getById(@PathVariable UUID id) {
        return new ResponseType<>(botConfigService.findById(id));
    }

    @PostMapping
    @ApiOperationBadRequest(summary = "新增 Bot 設定", description = "建立新的 Bot 設定。")
    public ResponseType<BotConfigVo> create(@RequestBody BotConfigVo vo) {
        return new ResponseType<>(botConfigService.create(vo));
    }

    @PutMapping("/{id}")
    @ApiOperationBadRequest(summary = "更新 Bot 設定", description = "依 ID 更新 Bot 設定。")
    public ResponseType<BotConfigVo> update(@PathVariable UUID id, @RequestBody BotConfigVo vo) {
        return new ResponseType<>(botConfigService.update(id, vo));
    }

    @DeleteMapping("/{id}")
    @ApiOperationBadRequest(summary = "刪除 Bot 設定", description = "依 ID 刪除 Bot 設定。")
    public ResponseType<Void> delete(@PathVariable UUID id) {
        botConfigService.delete(id);
        return new ResponseType<>(null);
    }
}
