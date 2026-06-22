package com.example.BackendArchitectureLab.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "api_usage_log")
public class ApiUsageLog extends BaseEntity {

    @Column(name = "service", nullable = false)
    private String service;

    @Column(name = "call_type", nullable = false)
    private String callType;

    @Column(name = "input_unit")
    private String inputUnit;

    @Column(name = "input_amount")
    private Long inputAmount;

    @Column(name = "estimated_cost", precision = 10, scale = 4)
    private BigDecimal estimatedCost;

    @Column(name = "user_id")
    private String userId;
}
