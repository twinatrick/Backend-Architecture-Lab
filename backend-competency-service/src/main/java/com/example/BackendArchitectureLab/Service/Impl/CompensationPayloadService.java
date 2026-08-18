package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.Service.ICompensationPayloadService;
import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * CompensationPayloadService - {@link ICompensationPayloadService} 實作。
 */
@Service
@RequiredArgsConstructor
public class CompensationPayloadService implements ICompensationPayloadService {

    private final ObjectMapper objectMapper;

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
            // 不將完整 payload 寫入錯誤訊息（其中含 userId/skillId/levelId/expectedVersion 等業務資料），
            // 僅記錄長度以防止機敏資料洩漏至 log / DB lastError。
            throw new IllegalStateException(
                    "Failed to deserialize compensation event payload: payloadLength="
                            + (payload != null ? payload.length() : 0), e);
        }
    }
}
