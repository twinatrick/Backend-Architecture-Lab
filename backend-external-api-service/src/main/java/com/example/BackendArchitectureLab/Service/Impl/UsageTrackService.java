package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Config.BotConfigLoader;
import com.example.BackendArchitectureLab.DataAccess.IApiUsageLogDataAccess;
import com.example.BackendArchitectureLab.Entity.ApiUsageLog;
import com.example.BackendArchitectureLab.Service.IUsageTrackService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UsageTrackService implements IUsageTrackService {

    private final IApiUsageLogDataAccess apiUsageLogDataAccess;
    private final BotConfigLoader botConfigLoader;

    private static final Map<String, BigDecimal> COST_PER_UNIT = Map.of(
            "stt", new BigDecimal("0.010"),
            "tts", new BigDecimal("0.005"),
            "chat", new BigDecimal("0.001")
    );

    @Override
    public boolean track(String service, String callType, String inputUnit, Long inputAmount) {
        BigDecimal unitCost = COST_PER_UNIT.getOrDefault(callType, BigDecimal.ZERO);
        BigDecimal estimatedCost = unitCost.multiply(BigDecimal.valueOf(inputAmount))
                .setScale(6, RoundingMode.HALF_UP);

        String dailyLimitStr = botConfigLoader.get(service, "cost_limit_daily");
        if (dailyLimitStr != null) {
            BigDecimal dailyLimit = new BigDecimal(dailyLimitStr);
            BigDecimal todayUsage = getTodayUsage(service);
            if (todayUsage.add(estimatedCost).compareTo(dailyLimit) > 0) {
                return false;
            }
        }

        ApiUsageLog log = new ApiUsageLog();
        log.setService(service);
        log.setCallType(callType);
        log.setInputUnit(inputUnit);
        log.setInputAmount(inputAmount);
        log.setEstimatedCost(estimatedCost);
        apiUsageLogDataAccess.save(log);
        return true;
    }

    private BigDecimal getTodayUsage(String service) {
        LocalDate today = LocalDate.now();
        Date start = Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date end = Date.from(today.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant());
        return apiUsageLogDataAccess.findByServiceAndCreatedTimeBetween(
                service, start, end).stream()
                .map(ApiUsageLog::getEstimatedCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
