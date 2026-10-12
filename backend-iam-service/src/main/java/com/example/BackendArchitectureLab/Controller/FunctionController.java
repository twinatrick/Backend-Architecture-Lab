package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Vo.Search.FunctionSearchQuery;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Service.IFunctionCommandService;
import com.example.BackendArchitectureLab.Service.IFunctionHierarchyService;
import com.example.BackendArchitectureLab.Service.IFunctionQueryService;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Vo.FunctionTransVo;
import com.example.BackendArchitectureLab.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/function")
@RequiredArgsConstructor
@ApiControllerTag(name = "Functions", description = "功能管理相關 API")
public class FunctionController {
    private final IFunctionCommandService functionCommandService;
    private final IFunctionQueryService functionQueryService;
    private final IFunctionHierarchyService functionHierarchyService;

    @PostMapping("/add")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "新增功能", description = "建立新的功能項目。")
    public ResponseType<?> addFunction(@RequestBody FunctionVo function) {
        functionCommandService.addFunction(function);
        return ResponseType.Success("功能新增成功");
    }

    @PostMapping("/update")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "更新功能", description = "更新一筆既有的功能。")
    public ResponseType<String> updateFunction(@RequestBody FunctionVo function) {
        functionCommandService.updateFunction(function);
        return ResponseType.Success("功能更新成功");
    }

    @PostMapping("/delete")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "刪除功能", description = "刪除一筆功能。")
    public ResponseType<String> deleteFunction(@RequestBody FunctionVo function) {
        functionCommandService.deleteFunction(function);
        return ResponseType.Success("功能刪除成功");
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequirePermission("View")
    @ApiOperationOk(summary = "串流分塊取得功能列表", description = "透過 SSE 串流分塊取得功能列表，每批次預設 250 筆")
    public Flux<ServerSentEvent<List<FunctionVo>>> streamFunctions(
            @RequestParam(name = "chunkSize", defaultValue = "250") int chunkSize) {
        return functionQueryService.streamFunctionsChunked(chunkSize);
    }

    @GetMapping("/{id}")
    @RequirePermission("View")
    @ApiOperationOk(summary = "依 ID 取得功能", description = "根據功能 ID 取得功能詳細資訊。")
    public ResponseType<FunctionVo> getFunctionById(@PathVariable String id) {
        return ResponseType.Success(functionQueryService.getFunctionById(id), "功能查詢成功");
    }

    @PostMapping("/search")
    @RequirePermission("View")
    @ApiOperationOk(summary = "分頁搜尋功能", description = "搜尋功能並回傳分頁結果，支援多種查詢條件與排序")
    public ResponseType<PageResult<FunctionVo>> searchFunctions(@Valid @RequestBody FunctionSearchQuery query) {
        PageResult<FunctionVo> result = functionQueryService.searchFunctions(query);
        return ResponseType.Success(result, "功能查詢成功");
    }

    @PostMapping("/saveAllFunction")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "批次儲存功能變更", description = "套用功能刪除，並儲存新增或更新的功能。")
    public ResponseType<?> saveAllFunction(@RequestBody FunctionTransVo function) {
        functionCommandService.deleteFunction(function.getDeleteFunction());
        functionCommandService.saveFunction(function.getSaveMainFunction());
        functionHierarchyService.saveFunctionNewChild(function.getSaveFunctionNewChild());
        return ResponseType.Success("功能保存成功");
    }
}
