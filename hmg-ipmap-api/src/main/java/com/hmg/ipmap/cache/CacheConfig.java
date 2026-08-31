package com.hmg.ipmap.cache;

import com.hmg.ipmap.common.context.UserContextTaskDecorator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.retry.annotation.EnableRetry;

@EnableRetry
@Configuration
public class CacheConfig {

    @Bean(destroyMethod = "close")
    public ExecutorService virtualExecutor() {
        ThreadFactory factory = Thread.ofVirtual().name("vt-cache-sync-", 0).factory();
        return Executors.newThreadPerTaskExecutor(factory);
    }

    @Bean(name = "cacheSyncTaskExecutor")
    public AsyncTaskExecutor cacheSyncTaskExecutor(ExecutorService virtualExecutor) {
        TaskExecutorAdapter adapter = new TaskExecutorAdapter(virtualExecutor);
        adapter.setTaskDecorator(new UserContextTaskDecorator());
        return adapter;
    }
}
