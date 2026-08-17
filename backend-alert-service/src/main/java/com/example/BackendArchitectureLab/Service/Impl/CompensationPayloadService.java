package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Service.ICompensationPayloadService;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * CompensationPayloadService - {@link ICompensationPayloadService} 實作。
 */
@Service
public class CompensationPayloadService implements ICompensationPayloadService {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String serialize(CompensationEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Failed to serialize compensation event payload: " + event.getEventId(), e);
        }
    }

    @Override
    public CompensationEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, CompensationEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to deserialize compensation event payload: " + payload, e);
        }
    }
}
