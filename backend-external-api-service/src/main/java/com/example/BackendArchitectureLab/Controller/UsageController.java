package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Vo.ApiUsageLogVo;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Service.IApiUsageLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/external/usage")
@ApiControllerTag(name = "API Usage", description = "API 使用記錄查詢相關 API")
@RequiredArgsConstructor
public class UsageController {

    private final IApiUsageLogService apiUsageLogService;

    @GetMapping
    @ApiOperationOk(summary = "依時間區間查詢使用記錄", description = "依開始與結束時間查詢 API 使用記錄，可依服務篩選。")
    public ResponseType<List<ApiUsageLogVo>> getByRange(
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date end,
            @RequestParam(value = "service", required = false) String service) {
        return new ResponseType<>(apiUsageLogService.findByRange(start, end, service));
    }

    @GetMapping("/summary")
    @ApiOperationOk(summary = "取得使用摘要", description = "依時間區間取得 API 使用統計摘要，可依服務篩選。")
    public ResponseType<Map<String, Object>> getSummary(
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date end,
            @RequestParam(value = "service", required = false) String service) {
        return new ResponseType<>(apiUsageLogService.getSummary(start, end, service));
    }
}
