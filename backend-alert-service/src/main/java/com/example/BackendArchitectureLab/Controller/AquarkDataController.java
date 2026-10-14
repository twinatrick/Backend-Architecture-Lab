package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Annotation.Ignore;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiBody;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiControllerTag;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationBadRequest;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiOperationOk;
import com.example.BackendArchitectureLab.Annotation.OpenApi.ApiQuery;
import com.example.BackendArchitectureLab.Annotation.RequirePermission;
import com.example.BackendArchitectureLab.Service.IAquarkDataQueryService;
import com.example.BackendArchitectureLab.Vo.AquarkUse.AquarkDataRaw;
import com.example.BackendArchitectureLab.Vo.AquarkUse.AverageAquark;
import com.example.BackendArchitectureLab.Vo.AquarkUse.TimeRange;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.ResponseType;
import com.example.BackendArchitectureLab.Vo.Search.AquarkDataSearchQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

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
    public ResponseType<PageResult<AquarkDataRaw>> searchAquarkData(@ApiBody(description = "Aquark 資料搜尋條件") AquarkDataSearchQuery query) {
        return ResponseType.Success(aquarkDataQueryService.searchAquarkData(query), "查詢成功");
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequirePermission("View")
    @ApiOperationOk(summary = "串流分塊取得 Aquark 水情資料", description = "透過 Server-Sent Events (SSE) 分塊串流取得 Aquark 水情時序資料，支援 chunkSize 參數 (預設 250，範圍 10~1000)")
    public Flux<ServerSentEvent<List<AquarkDataRaw>>> streamAquarkData(
            @ApiQuery(name = "chunkSize", defaultValue = "250", description = "串流分塊大小", example = "250") int chunkSize) {
        return aquarkDataQueryService.streamAquarkDataChunked(chunkSize);
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
    public ResponseType<List<AverageAquark>> getAverage(@ApiBody(description = "時間區間查詢條件") TimeRange time) {
        return new ResponseType<>(aquarkDataQueryService.getAverageAquark(time.getStart(), time.getEnd()));
    }
}
