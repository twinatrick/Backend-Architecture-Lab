package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Service.IAquarkDataService;
import com.example.BackendArchitectureLab.Annotation.Ingnore;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Dto.Vo.ResponseType;
import com.example.BackendArchitectureLab.Dto.Vo.AquarkUse.AquarkDataRaw;
import com.example.BackendArchitectureLab.Dto.Vo.AquarkUse.AverageAquark;
import com.example.BackendArchitectureLab.Dto.Vo.AquarkUse.CriteriaAPIFilter;
import com.example.BackendArchitectureLab.Dto.Vo.AquarkUse.TimeRange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/aquarkData")
@ApiControllerTag(name = "AquarkData", description = "Backend API endpoints - Aquark data queries")
public class AquarkDataController {
    @Autowired
    private IAquarkDataService aquarkDataService;

    @PostMapping("/getData")
    @RequirePermission({"System", "AquarkData", "View"})
    @ApiOperationBadRequest(summary = "Get aquark data", description = "Returns aquark data filtered by criteria.")
    public ResponseType<List<AquarkDataRaw>> getData(@RequestBody List<CriteriaAPIFilter> fillterList) {


        return new ResponseType<>(aquarkDataService.getAquarkDataWithFilter(fillterList));
    }

    @GetMapping("/getColumnNameList")
    @RequirePermission({"System", "AquarkData", "View"})
    @ApiOperationOk(summary = "Get column names", description = "Returns available aquark data column names.")
    public ResponseType<List<String>> getColumnNameList() {
        return new ResponseType<>(aquarkDataService.getColumnNameList());
    }

    @Ingnore
    @PostMapping("/getAverage")
    @ApiOperationBadRequest(summary = "Get average aquark data", description = "Returns averaged aquark data within a time range.")
    public ResponseType<List<AverageAquark>> getAverage(@RequestBody TimeRange time) {
        return new ResponseType<>(aquarkDataService.getAverageAquark(time.getStart(), time.getEnd()));
    }
}
