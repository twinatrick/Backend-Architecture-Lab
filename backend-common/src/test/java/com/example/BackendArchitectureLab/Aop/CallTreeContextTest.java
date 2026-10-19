package com.example.BackendArchitectureLab.Aop;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class CallTreeContextTest {

    @AfterEach
    void tearDown() {
        CallTreeContext.clear();
    }

    @Test
    @DisplayName("當未開啟診斷模式時，push 應直接回傳 null 且不佔用記憶體")
    void push_WhenDiagnosticModeDisabled_ReturnsNull() {
        CallTreeContext.init(false);

        InvocationNode node = CallTreeContext.push("SERVICE", "UserService", "getUserById");

        assertThat(node).isNull();
        assertThat(CallTreeContext.isDiagnostic()).isFalse();
        assertThat(CallTreeContext.finalizeAndGetRoot()).isNull();
    }

    @Test
    @DisplayName("診斷模式下應正確計算父子階層百分比、自身耗時與方法聚合統計")
    void pushAndPop_CalculatesPercentagesAndSelfTimeCorrectly() {
        CallTreeContext.init(true);

        InvocationNode controllerNode = CallTreeContext.push("CONTROLLER", "UserController", "searchUsers");
        controllerNode.setStartNanos(1_000_000L);

        InvocationNode serviceNode = CallTreeContext.push("SERVICE", "UserService", "searchUsers");
        serviceNode.setStartNanos(2_000_000L);

        // 第 1 次呼叫 Repository
        InvocationNode repoNode1 = CallTreeContext.push("REPOSITORY", "UserRepository", "findAll");
        repoNode1.setStartNanos(3_000_000L);
        repoNode1.setDurationNanos(20_000_000L); // 20ms
        CallTreeContext.pop(repoNode1, true, null);

        // 第 2 次呼叫同一 Repository 方法 (模擬迴圈 N+1)
        InvocationNode repoNode2 = CallTreeContext.push("REPOSITORY", "UserRepository", "findAll");
        repoNode2.setStartNanos(25_000_000L);
        repoNode2.setDurationNanos(50_000_000L); // 50ms
        CallTreeContext.pop(repoNode2, true, null);

        serviceNode.setDurationNanos(80_000_000L); // 80ms
        CallTreeContext.pop(serviceNode, true, null);

        controllerNode.setDurationNanos(100_000_000L); // 100ms
        CallTreeContext.pop(controllerNode, true, null);

        InvocationNode root = CallTreeContext.finalizeAndGetRoot();

        assertThat(root).isNotNull();
        assertThat(root.getClassName()).isEqualTo("UserController");
        assertThat(root.getParentPercentage()).isEqualTo(100.0);
        // Controller 自身耗時 = 100ms - 80ms = 20ms
        assertThat(root.getSelfNanos()).isEqualTo(20_000_000L);
        assertThat(root.getSelfPercentage()).isEqualTo(20.0);

        // Service 子節點
        assertThat(root.getChildren()).hasSize(1);
        InvocationNode serviceChild = root.getChildren().get(0);
        assertThat(serviceChild.getClassName()).isEqualTo("UserService");
        // Service 佔 Controller 百分比 = 80ms / 100ms = 80.0%
        assertThat(serviceChild.getParentPercentage()).isEqualTo(80.0);
        // Service 自身耗時 = 80ms - (20ms + 50ms) = 10ms
        assertThat(serviceChild.getSelfNanos()).isEqualTo(10_000_000L);
        assertThat(serviceChild.getSelfPercentage()).isEqualTo(12.5);

        // Service 之下的兩次 Repository 呼叫
        assertThat(serviceChild.getChildren()).hasSize(2);
        InvocationNode childRepo1 = serviceChild.getChildren().get(0);
        InvocationNode childRepo2 = serviceChild.getChildren().get(1);
        // Repo1 佔 Service 百分比 = 20ms / 80ms = 25.0%
        assertThat(childRepo1.getParentPercentage()).isEqualTo(25.0);
        // Repo2 佔 Service 百分比 = 50ms / 80ms = 62.5%
        assertThat(childRepo2.getParentPercentage()).isEqualTo(62.5);

        // Service 的方法聚合統計 (N+1 聚合視角)
        Collection<MethodAggregation> aggregations = serviceChild.getMethodAggregations().values();
        assertThat(aggregations).hasSize(1);
        MethodAggregation repoAgg = aggregations.iterator().next();
        assertThat(repoAgg.getClassName()).isEqualTo("UserRepository");
        assertThat(repoAgg.getMethodName()).isEqualTo("findAll");
        assertThat(repoAgg.getCalls()).isEqualTo(2);
        assertThat(repoAgg.getTotalDurationNanos()).isEqualTo(70_000_000L);
        assertThat(repoAgg.getMaxDurationNanos()).isEqualTo(50_000_000L);
        // 聚合總耗時佔父方法 Service 比例 = 70ms / 80ms = 87.5%
        assertThat(repoAgg.getParentPercentage()).isEqualTo(87.5);
    }

    @Test
    @DisplayName("當下游發生例外時應正確標註 ERROR 與錯誤訊息")
    void pop_WhenExceptionOccurs_RecordsErrorStatus() {
        CallTreeContext.init(true);

        InvocationNode root = CallTreeContext.push("CONTROLLER", "UserController", "getUser");
        InvocationNode service = CallTreeContext.push("SERVICE", "UserService", "getUser");

        RuntimeException error = new RuntimeException("Database timeout");
        CallTreeContext.pop(service, false, error);
        CallTreeContext.pop(root, false, error);

        InvocationNode result = CallTreeContext.finalizeAndGetRoot();

        assertThat(result.getStatus()).isEqualTo("ERROR");
        assertThat(result.getErrorMessage()).isEqualTo("Database timeout");
        assertThat(result.getChildren().get(0).getStatus()).isEqualTo("ERROR");
        assertThat(result.getChildren().get(0).getErrorMessage()).isEqualTo("Database timeout");
    }

    @Test
    @DisplayName("clear 應徹底清除 ThreadLocal")
    void clear_RemovesThreadLocalInstance() {
        CallTreeContext.init(true);
        assertThat(CallTreeContext.isDiagnostic()).isTrue();

        CallTreeContext.clear();
        assertThat(CallTreeContext.isDiagnostic()).isFalse();
        assertThat(CallTreeContext.get()).isNull();
    }
}
