package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Service.IAquarkDataQueryService;
import com.example.BackendArchitectureLab.Annotation.Ignore;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Vo.AquarkUse.AquarkDataRaw;
import com.example.BackendArchitectureLab.Vo.AquarkUse.AverageAquark;
import com.example.BackendArchitectureLab.Vo.AquarkUse.CriteriaAPIFilter;
import com.example.BackendArchitectureLab.Vo.AquarkUse.TimeRange;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.Search.AquarkDataSearchQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/aquarkData")
@RequiredArgsConstructor
@ApiControllerTag(name = "AquarkData", description = "Aquark 資料查詢相關 API")
public class AquarkDataController {
    private final IAquarkDataQueryService aquarkDataQueryService;

    @PostMapping("/search")
    @RequirePermission("View")
    @ApiOperationOk(summary = "搜尋 Aquark 水情資料（分頁）", description = "依條件分頁取得 Aquark 水情時序資料，支援 page, size, sortBy, sortDir 與 filters")
    public ResponseType<PageResult<AquarkDataRaw>> searchAquarkData(@Valid @RequestBody AquarkDataSearchQuery query) {
        return ResponseType.Success(aquarkDataQueryService.searchAquarkData(query), "查詢成功");
    }

    @PostMapping("/getData")
    @RequirePermission("View")
    @ApiOperationOk(summary = "取得 Aquark 資料（舊端點相容）", description = "舊端點強制安全截斷：若條件為空僅回傳最新 20 筆，防止全表掃描", deprecated = true)
    public ResponseType<List<AquarkDataRaw>> getData(@RequestBody(required = false) List<CriteriaAPIFilter> filterList) {
        // 安全防禦：轉調分頁查詢第一頁（size=20），杜絕加載全表
        AquarkDataSearchQuery query = new AquarkDataSearchQuery();
        query.setPage(0);
        query.setSize(20);
        query.setFilters(filterList != null ? filterList : List.of());
        PageResult<AquarkDataRaw> pageResult = aquarkDataQueryService.searchAquarkData(query);
        return new ResponseType<>(pageResult.getContent());
    }

    @GetMapping("/getColumnNameList")
    @RequirePermission("View")
    @ApiOperationOk(summary = "取得欄位名稱", description = "取得可用的 Aquark 資料欄位名稱。")
    public ResponseType<List<String>> getColumnNameList() {
        return new ResponseType<>(aquarkDataQueryService.getColumnNameList());
    }

    @Ignore
    @PostMapping("/getAverage")
    @ApiOperationBadRequest(summary = "取得 Aquark 平均資料", description = "取得時間區間內 Aquark 資料的平均值。")
    public ResponseType<List<AverageAquark>> getAverage(@RequestBody TimeRange time) {
        return new ResponseType<>(aquarkDataQueryService.getAverageAquark(time.getStart(), time.getEnd()));
    }
}
