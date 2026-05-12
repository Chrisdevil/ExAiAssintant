package com.example.exaiassistant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService embeddingExecutor() {
        return Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "embedding-pool");
            t.setDaemon(true);
            return t;
        });
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService memoryExtractExecutor() {
        return Executors.newFixedThreadPool(1, r -> {
            Thread t = new Thread(r, "memory-extract-pool");
            t.setDaemon(true);
            return t;
        });
    }
}
