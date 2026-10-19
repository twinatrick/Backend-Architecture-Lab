package com.example.BackendArchitectureLab.Filter;

import com.example.BackendArchitectureLab.Aop.CallTreeContext;
import com.example.BackendArchitectureLab.Aop.InvocationNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@RequiredArgsConstructor
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class DiagnosticTraceFilter extends OncePerRequestFilter {

    private static final String DIAGNOSTIC_HEADER = "X-Diagnostic-Trace";

    private final ObjectMapper objectMapper;

    @Value("${performance.diagnostic-trace.enabled:false}")
    private boolean globalDiagnosticEnabled;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String headerValue = request.getHeader(DIAGNOSTIC_HEADER);
        boolean isDiagnostic = "true".equalsIgnoreCase(headerValue) || globalDiagnosticEnabled;

        if (isDiagnostic) {
            CallTreeContext.init(true);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (isDiagnostic) {
                try {
                    InvocationNode root = CallTreeContext.finalizeAndGetRoot();
                    if (root != null) {
                        DiagnosticTraceLog traceLog = new DiagnosticTraceLog(
                                request.getMethod(),
                                request.getRequestURI(),
                                response.getStatus(),
                                root.getDurationMs(),
                                root,
                                root.getMethodAggregations().values()
                        );
                        log.info("FOUR_TIER_TELEMETRY: {}", objectMapper.writeValueAsString(traceLog));
                    }
                } catch (Throwable t) {
                    log.warn("Failed to serialize diagnostic telemetry: {}", t.getMessage());
                } finally {
                    CallTreeContext.clear();
                }
            }
        }
    }
}
