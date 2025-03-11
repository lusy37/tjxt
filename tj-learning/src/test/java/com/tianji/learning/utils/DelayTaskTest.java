package com.tianji.learning.utils;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.DelayQueue;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j

class DelayTaskTest {

    @Test
    void testDelayQueue() {

        // 初始化延迟队列
        DelayQueue<DelayTask<String>> queue = new DelayQueue<>();
        // 向延迟队列中添加任务
        log.info("开始初始化延迟任务");
        queue.add(new DelayTask<>("task1", Duration.ofSeconds(5)));
        queue.add(new DelayTask<>("task2", Duration.ofSeconds(10)));
        queue.add(new DelayTask<>("task3", Duration.ofSeconds(15)));
        log.info("尝试执行任务");
        // 从延迟队列中取出任务
        while (!queue.isEmpty()) {
            DelayTask<String> poll = null;
            try {
                poll = queue.take();
                log.info("执行任务：{}", poll.getData());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

}