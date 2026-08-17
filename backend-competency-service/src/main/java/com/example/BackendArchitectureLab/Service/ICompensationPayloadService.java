package com.example.BackendArchitectureLab.Service;

import com.example.BackendArchitectureLab.Vo.Kafka.CompensationEvent;

/**
 * ICompensationPayloadService - 補償事件 payload 序列化／反序列化。
 * <p>
 * 所有回復路徑皆以 {@code compensation_event_log.payload}（persisted payload）為
 * authoritative source，不重新信任 Kafka redelivery 的 payload，避免同一事件取得不同內容。
 */
public interface ICompensationPayloadService {

    /**
     * 將補償事件序列化為 JSON 字串（用於認領時快照存入 DB）。
     *
     * @param event 補償事件
     * @return JSON 字串
     * @throws IllegalArgumentException 序列化失敗
     */
    String serialize(CompensationEvent event);

    /**
     * 由 JSON 字串還原補償事件。
     *
     * @param payload persisted payload
     * @return 還原的補償事件
     * @throws IllegalStateException payload 損毀或格式不符
     */
    CompensationEvent deserialize(String payload);
}
