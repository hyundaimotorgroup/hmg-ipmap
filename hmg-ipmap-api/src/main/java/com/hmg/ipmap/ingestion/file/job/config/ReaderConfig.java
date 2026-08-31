package com.hmg.ipmap.ingestion.file.job.config;

import com.hmg.ipmap.ingestion.file.job.JobParameter;
import com.hmg.ipmap.ingestion.file.job.reader.ClaimingItemReader;
import com.hmg.ipmap.ingestion.file.job.reader.RawLineData;
import com.hmg.ipmap.ingestion.file.repository.BatchFileDetailRepository;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.support.SynchronizedItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReaderConfig {
    @Bean
    @StepScope
    public ClaimingItemReader claimingItemReader(
            BatchFileDetailRepository batchFileDetailRepository,
            JobParameter jobParameter,
            @Value("#{stepExecutionContext['fileType']}") String fileType,
            @Value("#{stepExecutionContext['claimSize']}") Integer claimedSize) {
        return new ClaimingItemReader(
                batchFileDetailRepository, jobParameter.getBatchId(), fileType, claimedSize);
    }

    @Bean
    public ItemReader<RawLineData> reader(ClaimingItemReader delegate) {
        return new SynchronizedItemReader<>(delegate);
    }
}
