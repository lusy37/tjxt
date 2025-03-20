package com.tianji.promotion.config;

import io.netty.util.concurrent.ThreadPerTaskExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@Slf4j
public class PromotionConfig {

    @Bean
    public Executor generateExchangeCodeExecutor() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 设置核心线程数大小
        executor.setCorePoolSize(2);
        // 设置线程池大小
        executor.setMaxPoolSize(5);
        // 设置队列大小
        executor.setQueueCapacity(200);
        // 设置线程名称
        executor.setThreadNamePrefix("exchange-code-handler-");
        // 设置拒绝策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        log.info("初始化生成兑换码的线程池结束...");
        return executor;
    }
}
