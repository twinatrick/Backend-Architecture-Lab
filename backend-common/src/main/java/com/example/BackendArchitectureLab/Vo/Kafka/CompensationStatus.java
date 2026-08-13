package com.example.BackendArchitectureLab.Vo.Kafka;

public final class CompensationStatus {
    public static final String TRANSACTION_STARTED = "TRANSACTION_STARTED";
    public static final String COMMITTED = "COMMITTED";
    public static final String FAILED = "FAILED";
    public static final String COMPENSATION_REQUIRED = "COMPENSATION_REQUIRED";
    public static final String COMPENSATED = "COMPENSATED";

    private CompensationStatus() {}
}
