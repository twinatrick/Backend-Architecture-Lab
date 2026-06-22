package com.example.BackendArchitectureLab.Feign;

import com.example.BackendArchitectureLab.Vo.AlertCheckLimitVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "alert-service")
public interface AlertCheckLimitFeignClient {

    @GetMapping("/alertCheckLimit/inner/all")
    List<AlertCheckLimitVo> getLimit();

    @GetMapping("/alertCheckLimit/inner/by-table-column")
    AlertCheckLimitVo getLimitByTableAndColumn(@RequestParam("tableName") String tableName,
                                                @RequestParam("columnName") String columnName);

    @PostMapping("/alertCheckLimit/inner/insert")
    void insertLimit(@RequestParam("tableName") String tableName,
                     @RequestParam("columnName") String columnName,
                     @RequestParam("maxChange") int maxChange);
}
