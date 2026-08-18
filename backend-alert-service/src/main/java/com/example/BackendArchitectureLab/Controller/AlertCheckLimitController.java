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
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@RestController
@RequestMapping("/alertCheckLimit")
@RequiredArgsConstructor
@RequirePermission(layer = "AlertLimit")
@ApiControllerTag(name = "Alert Limits", description = "告警門檻管理相關 API")
public class AlertCheckLimitController {
    private final IAlertCheckLimitService alertCheckLimitService;
    @PostMapping("/add")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "新增告警限制", description = "為資料表欄位建立或更新一筆告警限制。")
    public ResponseType<AlertCheckLimitVo> addLimit(@RequestBody AlertCheckLimitVo alertCheckLimitVo) {
        return ResponseType.Success(alertCheckLimitService.addLimit(alertCheckLimitVo), "告警限制新增成功");
    }

    @GetMapping("/get")
    @RequirePermission("View")
    @ApiOperationOk(summary = "取得告警限制列表", description = "回傳所有告警限制。")
    public ResponseType<List<AlertCheckLimitVo>> getLimit() {
        return ResponseType.Success(alertCheckLimitService.getLimit(), "告警限制查詢成功");
    }

    @PostMapping("/update")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "更新告警限制", description = "更新一筆既有的告警限制。")
    public ResponseType<AlertCheckLimitVo> updateLimit(@RequestBody AlertCheckLimitVo alertCheckLimitVo) {
        return ResponseType.Success(alertCheckLimitService.updateLimit(alertCheckLimitVo), "告警限制更新成功");
    }

    @PostMapping("/delete")
    @RequirePermission("Edit")
    @ApiOperationBadRequest(summary = "刪除告警限制", description = "刪除一筆告警限制。")
    public ResponseType<String> deleteLimit(@RequestBody AlertCheckLimitVo alertCheckLimitVo) {
        alertCheckLimitService.deleteLimit(alertCheckLimitVo);
        return ResponseType.Success("告警限制刪除成功");
    }
    
    @PostMapping("/search")
    @RequirePermission("View")
    @ApiOperationBadRequest(summary = "搜尋告警檢查限制（分頁）", description = "支援 tableName、columnName、limitValue 範圍、createdBy 查詢條件，預設按 createdTime 降序排序")
    public ResponseType<PageResult<AlertCheckLimitVo>> searchAlertCheckLimits(@RequestBody AlertCheckLimitSearchQuery query) {
        return ResponseType.Success(alertCheckLimitService.searchAlertCheckLimits(query), "告警檢查限制查詢成功");
    }
}
