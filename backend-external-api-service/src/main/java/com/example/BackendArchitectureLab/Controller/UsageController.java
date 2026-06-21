package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Dto.Vo.ApiUsageLogVo;
import com.example.BackendArchitectureLab.Dto.Vo.ResponseType;
import com.example.BackendArchitectureLab.Service.IApiUsageLogService;
import org.springframework.beans.factory.annotation.Autowired;
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
public class UsageController {

    @Autowired
    private IApiUsageLogService apiUsageLogService;

    @GetMapping
    public ResponseType<List<ApiUsageLogVo>> getByRange(
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date end,
            @RequestParam(value = "service", required = false) String service) {
        return new ResponseType<>(apiUsageLogService.findByRange(start, end, service));
    }

    @GetMapping("/summary")
    public ResponseType<Map<String, Object>> getSummary(
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date end,
            @RequestParam(value = "service", required = false) String service) {
        return new ResponseType<>(apiUsageLogService.getSummary(start, end, service));
    }
}
