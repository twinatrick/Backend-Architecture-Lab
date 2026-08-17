package com.example.BackendArchitectureLab.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CompensationOutboxThreadPoolConfig - 補償 Outbox 發佈的共用執行緒池（application-scoped）。
 * 由 CompensationOutboxWorker 注入共用，避免每次批次 flush 都重新建立/銷毀 thread pool
 * （M-03：重複建立 daemon pool 且批次逾時時殘留工作依賴 daemon 收尾）。
 * 執行緒為 daemon，由容器關閉時統一 shutdown，不阻擋 JVM 結束。
 */
@Configuration
public class CompensationOutboxThreadPoolConfig {

    @Value("${compensation.outbox.publish-parallelism:4}")
    private int publishParallelism;

    @Bean(destroyMethod = "shutdown")
    public ExecutorService compensationOutboxPublisherPool() {
        return Executors.newFixedThreadPool(Math.max(1, publishParallelism), runnable -> {
            Thread thread = new Thread(runnable, "compensation-outbox-publisher");
            thread.setDaemon(true);
            return thread;
        });
    }
}
