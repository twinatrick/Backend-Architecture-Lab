package com.example.BackendArchitectureLab.Dto.Kafka;

public final class CompensationStatus {
    public static final String SAVE_POINT = "SAVE_POINT";
    public static final String COMMITTED = "COMMITTED";
    public static final String COMPENSATED = "COMPENSATED";
    public static final String FAILED = "FAILED";

    private CompensationStatus() {}
}
