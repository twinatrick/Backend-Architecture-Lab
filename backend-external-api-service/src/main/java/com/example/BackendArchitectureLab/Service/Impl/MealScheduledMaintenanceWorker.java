package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Service.IMealLineService;
import com.example.BackendArchitectureLab.Service.IMealRestaurantLocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
@RequiredArgsConstructor
public class MealScheduledMaintenanceWorker {
    private final IMealRestaurantLocationService locationService;
    private final IMealLineService lineService;

    @Scheduled(cron = "${meals.location.check-cron:0 15 3 1 1,4,7,10 *}", zone = "${meals.timezone:Asia/Taipei}")
    public void runQuarterlyLocationChecks() {
        locationService.runQuarterlyStatusChecks(new Date());
    }

    @Scheduled(cron = "${meals.line.daily-summary-cron:0 0 19 * * *}", zone = "${meals.timezone:Asia/Taipei}")
    public void runDailySummaryNotificationDispatch() {
        lineService.dispatchDueNotifications(new Date());
    }

    @Scheduled(fixedDelayString = "${meals.line.draft-cleanup-delay-ms:300000}")
    public void runExpiredDraftCleanup() {
        lineService.expireStaleDrafts(new Date());
    }
}
