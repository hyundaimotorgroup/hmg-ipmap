package com.hmg.ipmap.common.config;

import com.hmg.ipmap.common.context.UserContextTaskDecorator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@Configuration
@EnableAsync
@RequiredArgsConstructor
public class AsyncConfig implements AsyncConfigurer {

    private final AsyncProperties asyncProperties;

    @Bean(name = "applicationTaskExecutor")
    public TaskExecutor taskExecutor() {
        AsyncProperties.ThreadPool props = asyncProperties.getApplicationTaskExecutor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.getCorePoolSize());
        executor.setMaxPoolSize(props.getMaxPoolSize());
        executor.setQueueCapacity(props.getQueueCapacity());
        executor.setTaskDecorator(new UserContextTaskDecorator());
        executor.initialize();
        return executor;
    }

    @Bean(name = "fileImportTaskExecutor")
    public TaskExecutor fileImportTaskExecutor() {
        AsyncProperties.ThreadPool props = asyncProperties.getFileImportTaskExecutor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.getCorePoolSize());
        executor.setMaxPoolSize(props.getMaxPoolSize());
        executor.setQueueCapacity(props.getQueueCapacity());
        executor.setThreadNamePrefix("file-import-");
        executor.setTaskDecorator(new UserContextTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(300);
        executor.initialize();
        return executor;
    }

    @Bean(name = "jobRunnerTaskExecutor")
    public TaskExecutor jobRunnerTaskExecutor() {
        AsyncProperties.ThreadPool props = asyncProperties.getJobRunnerTaskExecutor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.getCorePoolSize());
        executor.setMaxPoolSize(props.getMaxPoolSize());
        executor.setQueueCapacity(props.getQueueCapacity());
        executor.setThreadNamePrefix("job-runner-");
        executor.setTaskDecorator(new UserContextTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(300);
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
                log.error(
                        "Uncaught exception in async method '{}': {}",
                        method.getName(),
                        ex.getMessage(),
                        ex);
    }
}
