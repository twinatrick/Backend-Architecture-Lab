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
@ApiControllerTag(name = "Functions", description = "Backend API endpoints - Function management")
public class FunctionController {
    @Autowired
    private IFunctionService functionService;

    @PostMapping("/add")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "Add function", description = "Creates a new function entry.")
    public ResponseType<?> addFunction(@RequestBody FunctionVo function) {
        functionService.addFunction(function);
        return ResponseType.Success("Function added successfully");
    }

    @PostMapping("/update")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "Update function", description = "Updates an existing function.")
    public ResponseType<String> updateFunction(@RequestBody FunctionVo function) {
        functionService.updateFunction(function);
        return ResponseType.Success("Function updated successfully");
    }

    @PostMapping("/delete")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "Delete function", description = "Deletes a function.")
    public ResponseType<String> deleteFunction(@RequestBody FunctionVo function) {
        functionService.deleteFunction(function);
        return ResponseType.Success("Function deleted successfully");
    }

    @GetMapping("/get")
    @RequirePermission("View")
    @ApiOperationOk(summary = "Get functions", description = "Returns all functions.")
    public ResponseType<List<FunctionVo>> getFunction() {
        return ResponseType.Success(functionService.getFunction(), "Functions fetched successfully");
    }

    @PostMapping("/saveAllFunction")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "Save function changes", description = "Applies function deletions and saves new or updated functions.")
    public ResponseType<?> saveAllFunction(@RequestBody FunctionTransVo function) {
        functionService.deleteFunction(function.getDeleteFunction());
        functionService.saveFunction(function.getSaveMainFunction());
        functionService.saveFunctionNewChild(function.getSaveFunctionNewChild());
        return ResponseType.Success("Functions saved successfully");
    }
    
    @PostMapping("/search")
    @RequirePermission("View")
    @ApiOperationOk(summary = "Search functions with pagination", description = "搜尋功能並回傳分頁結果，支援多種查詢條件與排序")
    public ResponseType<PageResult<FunctionVo>> searchFunctions(@Valid @RequestBody FunctionSearchQuery query) {
        PageResult<FunctionVo> result = functionService.searchFunctions(query);
        return ResponseType.Success(result, "Functions fetched successfully");
    }
}
