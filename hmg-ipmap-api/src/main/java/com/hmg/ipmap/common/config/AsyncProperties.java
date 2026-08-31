package com.hmg.ipmap.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.async")
public class AsyncProperties {

    private ThreadPool applicationTaskExecutor = new ThreadPool(10, 50, 100);
    private ThreadPool fileImportTaskExecutor = new ThreadPool(2, 4, 10);
    private ThreadPool jobRunnerTaskExecutor = new ThreadPool(2, 4, 10);

    @Data
    public static class ThreadPool {
        private int corePoolSize;
        private int maxPoolSize;
        private int queueCapacity;

        public ThreadPool(int corePoolSize, int maxPoolSize, int queueCapacity) {
            this.corePoolSize = corePoolSize;
            this.maxPoolSize = maxPoolSize;
            this.queueCapacity = queueCapacity;
        }
    }
}
