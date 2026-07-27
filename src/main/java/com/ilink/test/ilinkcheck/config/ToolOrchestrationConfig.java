package com.ilink.test.ilinkcheck.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class ToolOrchestrationConfig {

    @Bean(name = "toolExecutionExecutor", destroyMethod = "shutdown")
    public Executor toolExecutionExecutor() {
        return Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors()));
    }
}
