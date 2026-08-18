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
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/aquarkData")
@RequiredArgsConstructor
@ApiControllerTag(name = "AquarkData", description = "Aquark 資料查詢相關 API")
public class AquarkDataController {
    private final IAquarkDataQueryService aquarkDataQueryService;

    @PostMapping("/getData")
    @RequirePermission("View")
    @ApiOperationBadRequest(summary = "取得 Aquark 資料", description = "依條件取得 Aquark 資料。")
    public ResponseType<List<AquarkDataRaw>> getData(@RequestBody List<CriteriaAPIFilter> fillterList) {


        return new ResponseType<>(aquarkDataQueryService.getAquarkDataWithFilter(fillterList));
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
