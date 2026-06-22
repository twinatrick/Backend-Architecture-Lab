package com.example.BackendArchitectureLab.Vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiUsageLogVo {
    private UUID id;
    private String service;
    private String callType;
    private Long inputAmount;
    private BigDecimal inputUnit;
    private BigDecimal estimatedCost;
    private Date createdTime;
    private Date updatedTime;
}
