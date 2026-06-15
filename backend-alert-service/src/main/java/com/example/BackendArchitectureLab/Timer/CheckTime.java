package com.example.BackendArchitectureLab.Timer;

import com.example.BackendArchitectureLab.Service.ICheckApiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Date;

@Slf4j
@Component
public class CheckTime {

    @Autowired
    private ICheckApiService checkApiService;

    @Scheduled(cron = "0 30 * * * *")
    public void runCheckApi() {
        try {
            Date now = new Date();
            checkApiService.getAquarkApiData();
            long executeTime = System.currentTimeMillis() - now.getTime();
            log.info("CheckTime.runCheckApi: {}s", executeTime / 1000);
        } catch (IOException e) {
            log.error("CheckApi 執行失敗: {}", e.toString());
            throw new RuntimeException(e);
        }
    }
}
