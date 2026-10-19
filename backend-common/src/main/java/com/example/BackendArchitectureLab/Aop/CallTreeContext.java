package com.example.BackendArchitectureLab.Aop;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayDeque;
import java.util.Deque;

@Getter
@Setter
public class CallTreeContext {

    private static final ThreadLocal<CallTreeContext> CURRENT = new ThreadLocal<>();

    private final boolean diagnosticMode;
    private final Deque<InvocationNode> stack = new ArrayDeque<>();
    private InvocationNode rootNode;
    private int sequenceCounter = 0;

    public CallTreeContext(boolean diagnosticMode) {
        this.diagnosticMode = diagnosticMode;
    }

    public static CallTreeContext init(boolean diagnosticMode) {
        CallTreeContext context = new CallTreeContext(diagnosticMode);
        CURRENT.set(context);
        return context;
    }

    public static CallTreeContext get() {
        return CURRENT.get();
    }

    public static boolean isDiagnostic() {
        CallTreeContext ctx = CURRENT.get();
        return ctx != null && ctx.diagnosticMode;
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static InvocationNode push(String layer, String className, String methodName) {
        CallTreeContext ctx = CURRENT.get();
        if (ctx == null || !ctx.diagnosticMode) {
            return null;
        }

        ctx.sequenceCounter++;
        InvocationNode node = InvocationNode.builder()
                .id(String.valueOf(ctx.sequenceCounter))
                .layer(layer)
                .className(className)
                .methodName(methodName)
                .startNanos(System.nanoTime())
                .status("SUCCESS")
                .build();

        if (ctx.stack.isEmpty()) {
            if (ctx.rootNode == null) {
                ctx.rootNode = node;
            }
        } else {
            ctx.stack.peek().addChild(node);
        }

        ctx.stack.push(node);
        return node;
    }

    public static void pop(InvocationNode expectedNode, boolean success, Throwable error) {
        if (expectedNode == null) {
            return;
        }
        CallTreeContext ctx = CURRENT.get();
        if (ctx == null || !ctx.diagnosticMode) {
            return;
        }

        while (!ctx.stack.isEmpty()) {
            InvocationNode top = ctx.stack.pop();
            if (top.getDurationNanos() <= 0) {
                top.setDurationNanos(System.nanoTime() - top.getStartNanos());
            }
            if (top == expectedNode) {
                if (!success) {
                    top.setStatus("ERROR");
                    top.setErrorMessage(error != null ? error.getMessage() : "Exception occurred");
                }
                break;
            } else {
                top.setStatus("ERROR");
                top.setErrorMessage("Abrupt termination");
            }
        }
    }

    public static InvocationNode finalizeAndGetRoot() {
        CallTreeContext ctx = CURRENT.get();
        if (ctx == null || ctx.rootNode == null) {
            return null;
        }
        ctx.rootNode.calculatePercentagesAndSelf(null);
        return ctx.rootNode;
    }
}
