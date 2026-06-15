package com.example.BackendArchitectureLab.Dto.Kafka;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompensationEvent {
    private UUID transactionId;
    private String serviceName;
    private String action;
    private String status;
    private Map<String, Object> beforeState;
    private Map<String, Object> afterState;
    private String errorMessage;
    private Instant timestamp;
}
