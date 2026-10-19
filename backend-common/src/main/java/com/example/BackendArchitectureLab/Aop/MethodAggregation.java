package com.example.BackendArchitectureLab.Aop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MethodAggregation {

    private String layer;
    private String className;
    private String methodName;
    private int calls;
    private long totalDurationNanos;
    private long maxDurationNanos;
    private double parentPercentage;

    public double getTotalMs() {
        return Math.round((totalDurationNanos / 1_000_000.0) * 100.0) / 100.0;
    }

    public double getMaxMs() {
        return Math.round((maxDurationNanos / 1_000_000.0) * 100.0) / 100.0;
    }

    public void recordCall(long durationNanos) {
        this.calls++;
        this.totalDurationNanos += durationNanos;
        if (durationNanos > this.maxDurationNanos) {
            this.maxDurationNanos = durationNanos;
        }
    }
}
