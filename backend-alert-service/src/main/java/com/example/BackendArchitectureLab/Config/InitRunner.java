package com.example.BackendArchitectureLab.Config;

import com.example.BackendArchitectureLab.Dto.Vo.AlertCheckLimitVo;
import com.example.BackendArchitectureLab.Service.IAlertCheckLimitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@ConditionalOnProperty(value = "app.init.enabled", havingValue = "true", matchIfMissing = true)
public class InitRunner {

    @Autowired
    private IAlertCheckLimitService alertCheckLimitService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        initAlertCheckLimits();
    }

    private void initAlertCheckLimits() {
        String[] columns = {"rain_d", "moisture", "temperature", "echo", "water_speed_aquark", "v1", "v2", "v3", "v4", "v5", "v6", "v7"};
        List<AlertCheckLimitVo> existingLimits = alertCheckLimitService.getLimit();

        if (existingLimits.isEmpty()) {
            Arrays.stream(columns).forEach(col ->
                alertCheckLimitService.insertLimit("aquark_data", col, 10)
            );
        } else {
            Arrays.stream(columns).forEach(col -> {
                AlertCheckLimitVo limit = alertCheckLimitService.getLimit("aquark_data", col);
                if (limit == null) {
                    alertCheckLimitService.insertLimit("aquark_data", col, 10);
                }
            });
        }
    }
}
