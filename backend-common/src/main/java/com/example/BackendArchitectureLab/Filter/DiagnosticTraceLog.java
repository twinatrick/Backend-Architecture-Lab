package com.example.BackendArchitectureLab.Filter;

import com.example.BackendArchitectureLab.Aop.InvocationNode;
import com.example.BackendArchitectureLab.Aop.MethodAggregation;

import java.util.Collection;

public record DiagnosticTraceLog(
        String method,
        String uri,
        int status,
        double totalDurationMs,
        InvocationNode callTree,
        Collection<MethodAggregation> methodSummary
) {}
