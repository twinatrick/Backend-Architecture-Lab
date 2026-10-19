package com.example.BackendArchitectureLab.Aop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvocationNode {

    private String id;
    private String layer;
    private String className;
    private String methodName;
    private long startNanos;
    private long durationNanos;
    private long selfNanos;
    private double selfPercentage;
    private double parentPercentage;
    private String status;
    private String errorMessage;

    @Builder.Default
    private List<InvocationNode> children = new ArrayList<>();

    @Builder.Default
    private Map<String, MethodAggregation> methodAggregations = new LinkedHashMap<>();

    public void addChild(InvocationNode child) {
        if (child != null) {
            this.children.add(child);
        }
    }

    public double getDurationMs() {
        return Math.round((durationNanos / 1_000_000.0) * 100.0) / 100.0;
    }

    public double getSelfMs() {
        return Math.round((selfNanos / 1_000_000.0) * 100.0) / 100.0;
    }

    public void calculatePercentagesAndSelf(InvocationNode parent) {
        if (parent == null) {
            this.parentPercentage = 100.0;
        } else {
            this.parentPercentage = parent.durationNanos > 0
                    ? Math.round(((double) this.durationNanos / parent.durationNanos * 100.0) * 10.0) / 10.0
                    : 0.0;
        }

        long childrenTotalNanos = 0;
        Map<String, MethodAggregation> aggregationMap = new LinkedHashMap<>();

        for (InvocationNode child : children) {
            child.calculatePercentagesAndSelf(this);
            childrenTotalNanos += child.durationNanos;

            String methodKey = child.className + "." + child.methodName;
            MethodAggregation agg = aggregationMap.computeIfAbsent(methodKey, k -> MethodAggregation.builder()
                    .layer(child.layer)
                    .className(child.className)
                    .methodName(child.methodName)
                    .calls(0)
                    .totalDurationNanos(0)
                    .maxDurationNanos(0)
                    .parentPercentage(0.0)
                    .build());
            agg.recordCall(child.durationNanos);
        }

        this.selfNanos = Math.max(0, this.durationNanos - childrenTotalNanos);
        this.selfPercentage = this.durationNanos > 0
                ? Math.round(((double) this.selfNanos / this.durationNanos * 100.0) * 10.0) / 10.0
                : 0.0;

        for (MethodAggregation agg : aggregationMap.values()) {
            double aggPct = this.durationNanos > 0
                    ? Math.round(((double) agg.getTotalDurationNanos() / this.durationNanos * 100.0) * 10.0) / 10.0
                    : 0.0;
            agg.setParentPercentage(aggPct);
        }

        this.methodAggregations = aggregationMap;
    }
}
