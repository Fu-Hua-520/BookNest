package com.fuhua.booknest.server.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * AI 机器人回复线程池。
 *
 * <p>为什么不直接用 {@code @Async} 的默认执行器：默认执行器是不可见的公共池，
 * 一旦模型调用把线程占满，会连带把通知、上传等其它异步任务一起拖住。
 * 这里单独给一条队列，出问题时影响面可控。</p>
 *
 * <p><b>拒绝策略用 AbortPolicy + 捕获</b>：队列满说明模型调用严重堆积，
 * 此时丢掉这次 @ 请求（并留下日志）比让发评论的线程同步阻塞更合理 ——
 * 评论本身已经落库了，AI 回复只是附加效果。</p>
 */
@Configuration
@Slf4j
public class AiBotExecutorConfig {

    /** 线程池 Bean 名，供注入使用 */
    public static final String EXECUTOR_BEAN_NAME = "aiBotReplyExecutor";

    /**
     * 机器人回复专用线程池。
     * 池小是刻意的：调用外部模型是慢 IO，且并发触发量本来就不高，
     * 真正限制吞吐的是对方的限流，不是本机线程数。
     */
    @Bean(name = EXECUTOR_BEAN_NAME)
    public TaskExecutor aiBotReplyExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("ai-bot-reply-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 优雅停机：等在跑的模型调用收尾，避免产生「已触发但没有回复」的悬空状态
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        log.info("AI 机器人回复线程池初始化完成: core=2, max=4, queue=100");
        return executor;
    }
}
