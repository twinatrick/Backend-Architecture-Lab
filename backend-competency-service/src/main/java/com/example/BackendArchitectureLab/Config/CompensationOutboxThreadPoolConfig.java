package com.example.BackendArchitectureLab.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CompensationOutboxThreadPoolConfig - 補償 Outbox 發佈的共用執行緒池（application-scoped）。
 * 由 CompensationOutboxWorker 注入共用，採用 Java 21 虛擬執行緒（Virtual Threads）執行器，
 * 消除作業系統平台執行緒等待 Kafka ACK 時的阻塞與資源消耗。
 */
@Configuration
public class CompensationOutboxThreadPoolConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService compensationOutboxPublisherPool() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
