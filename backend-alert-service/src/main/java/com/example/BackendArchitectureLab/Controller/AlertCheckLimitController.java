package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Vo.Search.AlertCheckLimitSearchQuery;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Service.IAlertCheckLimitService;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Vo.AlertCheckLimitVo;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@RestController
@RequestMapping("/alertCheckLimit")
@RequirePermission(layer = "AlertLimit")
@ApiControllerTag(name = "Alert Limits", description = "Backend API endpoints - Alert threshold management")
public class AlertCheckLimitController {
    @Autowired
    private IAlertCheckLimitService alertCheckLimitService;
    @PostMapping("/add")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "Add alert limit", description = "Creates or updates an alert limit for a table column.")
    public ResponseType<AlertCheckLimitVo> addLimit(@RequestBody AlertCheckLimitVo alertCheckLimitVo) {
        return ResponseType.Success(alertCheckLimitService.addLimit(alertCheckLimitVo), "Limit added successfully");
    }

    @GetMapping("/get")
    @RequirePermission("View")
    @ApiOperationOk(summary = "Get alert limits", description = "Returns all alert limits.")
    public ResponseType<List<AlertCheckLimitVo>> getLimit() {
        return ResponseType.Success(alertCheckLimitService.getLimit(), "Limits fetched successfully");
    }

    @PostMapping("/update")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "Update alert limit", description = "Updates an existing alert limit.")
    public ResponseType<AlertCheckLimitVo> updateLimit(@RequestBody AlertCheckLimitVo alertCheckLimitVo) {
        return ResponseType.Success(alertCheckLimitService.updateLimit(alertCheckLimitVo), "Limit updated successfully");
    }

    @PostMapping("/delete")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "Delete alert limit", description = "Deletes an alert limit.")
    public ResponseType<String> deleteLimit(@RequestBody AlertCheckLimitVo alertCheckLimitVo) {
        alertCheckLimitService.deleteLimit(alertCheckLimitVo);
        return ResponseType.Success("Limit deleted successfully");
    }
    
    @PostMapping("/search")
    @RequirePermission("View")
    @ApiOperationBadRequest(summary = "搜尋告警檢查限制（分頁）", description = "支援 tableName、columnName、limitValue 範圍、createdBy 查詢條件，預設按 createdTime 降序排序")
    public ResponseType<PageResult<AlertCheckLimitVo>> searchAlertCheckLimits(@RequestBody AlertCheckLimitSearchQuery query) {
        return ResponseType.Success(alertCheckLimitService.searchAlertCheckLimits(query), "告警檢查限制查詢成功");
    }
}
