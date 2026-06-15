package com.example.BackendArchitectureLab.Controller;

import com.example.BackendArchitectureLab.Dto.Vo.AlertCheckLimitVo;
import com.example.BackendArchitectureLab.Service.IAlertCheckLimitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/alertCheckLimit/inner")
public class AlertCheckLimitInternalController {
    @Autowired
    private IAlertCheckLimitService alertCheckLimitService;

    @GetMapping("/all")
    public List<AlertCheckLimitVo> getLimit() {
        return alertCheckLimitService.getLimit();
    }

    @GetMapping("/by-table-column")
    public AlertCheckLimitVo getLimitByTableAndColumn(@RequestParam String tableName,
                                                       @RequestParam String columnName) {
        return alertCheckLimitService.getLimit(tableName, columnName);
    }

    @PostMapping("/insert")
    public void insertLimit(@RequestParam String tableName,
                            @RequestParam String columnName,
                            @RequestParam int maxChange) {
        alertCheckLimitService.insertLimit(tableName, columnName, maxChange);
    }
}
