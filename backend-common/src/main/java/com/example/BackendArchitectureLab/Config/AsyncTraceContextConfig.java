package com.example.BackendArchitectureLab.Config;

import io.micrometer.context.ContextSnapshot;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.util.ClassUtils;

@Configuration
public class AsyncTraceContextConfig {

    @PostConstruct
    public void initContextPropagation() {
        if (ClassUtils.isPresent("reactor.core.publisher.Hooks", getClass().getClassLoader())) {
            reactor.core.publisher.Hooks.enableAutomaticContextPropagation();
        }
    }

    @Bean
    @ConditionalOnClass(ContextSnapshot.class)
    public TaskDecorator contextPropagatingTaskDecorator() {
        return runnable -> ContextSnapshot.captureAll().wrap(runnable);
    }
}
