package com.example.BackendArchitectureLab.Dto.Kafka;

public final class CompensationTopics {
    public static final String COMPENSATION = "transaction-compensation";
    public static final String SOCKET_SEND = "socketSend";

    private CompensationTopics() {}
}
