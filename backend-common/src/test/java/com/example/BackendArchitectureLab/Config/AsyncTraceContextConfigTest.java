package com.example.BackendArchitectureLab.Config;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskDecorator;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class AsyncTraceContextConfigTest {

    private final AsyncTraceContextConfig config = new AsyncTraceContextConfig();

    @Test
    void initContextPropagation_shouldExecuteWithoutException() {
        assertDoesNotThrow(config::initContextPropagation);
    }

    @Test
    void contextPropagatingTaskDecorator_shouldDecorateAndExecuteRunnable() {
        TaskDecorator decorator = config.contextPropagatingTaskDecorator();
        assertNotNull(decorator);

        AtomicBoolean executed = new AtomicBoolean(false);
        Runnable original = () -> executed.set(true);

        Runnable decorated = decorator.decorate(original);
        assertNotNull(decorated);

        decorated.run();
        assertTrue(executed.get());
    }
}
