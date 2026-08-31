package com.hmg.ipmap.common.config;

import org.springframework.batch.core.launch.support.JobOperatorFactoryBean;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskExecutor;

@Configuration
public class JobConfig {

    @Bean
    @Primary
    public JobOperatorFactoryBean jobOperatorFactoryBean(
            JobRepository jobRepository,
            @Qualifier("jobRunnerTaskExecutor") TaskExecutor taskExecutor) {

        JobOperatorFactoryBean jobOperatorFactoryBean = new JobOperatorFactoryBean();
        jobOperatorFactoryBean.setJobRepository(jobRepository);
        jobOperatorFactoryBean.setTaskExecutor(taskExecutor);

        return jobOperatorFactoryBean;
    }
}
