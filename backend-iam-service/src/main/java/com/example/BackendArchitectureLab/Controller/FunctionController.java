package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Vo.Search.FunctionSearchQuery;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Service.IFunctionService;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Vo.FunctionTransVo;
import com.example.BackendArchitectureLab.Vo.FunctionVo;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/function")
@ApiControllerTag(name = "Functions", description = "功能管理相關 API")
public class FunctionController {
    @Autowired
    private IFunctionService functionService;

    @PostMapping("/add")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "新增功能", description = "建立新的功能項目。")
    public ResponseType<?> addFunction(@RequestBody FunctionVo function) {
        functionService.addFunction(function);
        return ResponseType.Success("功能新增成功");
    }

    @PostMapping("/update")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "更新功能", description = "更新一筆既有的功能。")
    public ResponseType<String> updateFunction(@RequestBody FunctionVo function) {
        functionService.updateFunction(function);
        return ResponseType.Success("功能更新成功");
    }

    @PostMapping("/delete")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "刪除功能", description = "刪除一筆功能。")
    public ResponseType<String> deleteFunction(@RequestBody FunctionVo function) {
        functionService.deleteFunction(function);
        return ResponseType.Success("功能刪除成功");
    }

    @GetMapping("/get")
    @RequirePermission("View")
    @ApiOperationOk(summary = "取得功能列表", description = "回傳所有功能。")
    public ResponseType<List<FunctionVo>> getFunction() {
        return ResponseType.Success(functionService.getFunction(), "功能查詢成功");
    }

    @PostMapping("/saveAllFunction")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "批次儲存功能變更", description = "套用功能刪除，並儲存新增或更新的功能。")
    public ResponseType<?> saveAllFunction(@RequestBody FunctionTransVo function) {
        functionService.deleteFunction(function.getDeleteFunction());
        functionService.saveFunction(function.getSaveMainFunction());
        functionService.saveFunctionNewChild(function.getSaveFunctionNewChild());
        return ResponseType.Success("功能保存成功");
    }
    
    @PostMapping("/search")
    @RequirePermission("View")
    @ApiOperationOk(summary = "分頁搜尋功能", description = "搜尋功能並回傳分頁結果，支援多種查詢條件與排序")
    public ResponseType<PageResult<FunctionVo>> searchFunctions(@Valid @RequestBody FunctionSearchQuery query) {
        PageResult<FunctionVo> result = functionService.searchFunctions(query);
        return ResponseType.Success(result, "功能查詢成功");
    }
}
