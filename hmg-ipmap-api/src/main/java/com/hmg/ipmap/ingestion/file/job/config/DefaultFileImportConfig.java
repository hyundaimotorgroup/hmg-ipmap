package com.hmg.ipmap.ingestion.file.job.config;

import com.hmg.ipmap.ingestion.file.FileImportService;
import com.hmg.ipmap.ingestion.file.enums.DefaultFileType;
import com.hmg.ipmap.ingestion.file.job.dto.DefaultIpBlockDto;
import com.hmg.ipmap.ingestion.file.job.dto.DefaultLocationDto;
import com.hmg.ipmap.ingestion.file.job.listener.ClaimStepInfoForReaderListener;
import com.hmg.ipmap.ingestion.file.job.listener.ErrorPerChunkListener;
import com.hmg.ipmap.ingestion.file.job.listener.FileStepListener;
import com.hmg.ipmap.ingestion.file.job.listener.JobListener;
import com.hmg.ipmap.ingestion.file.job.processor.GenericFieldMapper;
import com.hmg.ipmap.ingestion.file.job.reader.DefaultIpBlockCsvReader;
import com.hmg.ipmap.ingestion.file.job.reader.DefaultLocationCsvReader;
import com.hmg.ipmap.ingestion.file.job.reader.RawLineData;
import com.hmg.ipmap.ingestion.file.job.writer.DefaultIpBlockWriter;
import com.hmg.ipmap.ingestion.file.job.writer.DefaultLocationWriter;
import com.hmg.ipmap.ingestion.file.repository.BatchFileDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@ConditionalOnProperty(name = "app.data-provider", havingValue = "default")
@Import(ReaderConfig.class)
@RequiredArgsConstructor
public class DefaultFileImportConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final BatchFileDetailRepository batchFileDetailRepository;

    @Value("${app.ingestion.location.chunk-size:500}")
    private Integer locationChunkSize;

    @Value("${app.ingestion.ip-block.chunk-size:100}")
    private Integer ipChunkSize;

    @Bean
    public Flow defaultLocationFlow(Step defaultLocationStep) {
        return new FlowBuilder<Flow>("flow_default_location").start(defaultLocationStep).build();
    }

    @Bean
    public Step defaultLocationStep(
            ItemReader<RawLineData> reader,
            DefaultLocationWriter defaultLocationWriter,
            FileStepListener fileStepListener,
            ErrorPerChunkListener errorPerChunkListener) {

        return new StepBuilder("step_default_location", jobRepository)
                .<RawLineData, DefaultLocationDto>chunk(locationChunkSize)
                .transactionManager(transactionManager)
                .reader(reader)
                .processor(
                        new GenericFieldMapper<>(
                                new DefaultLocationCsvReader(), batchFileDetailRepository))
                .writer(defaultLocationWriter)
                .listener(
                        new ClaimStepInfoForReaderListener(
                                DefaultFileType.LOCATION, locationChunkSize))
                .listener(fileStepListener)
                .listener(errorPerChunkListener)
                .build();
    }

    @Bean
    public Flow defaultIpBlockFlow(Step defaultIpBlockStep) {
        return new FlowBuilder<Flow>("flow_default_ip_block").start(defaultIpBlockStep).build();
    }

    @Bean
    public Step defaultIpBlockStep(
            ItemReader<RawLineData> reader,
            DefaultIpBlockWriter defaultIpBlockWriter,
            FileStepListener fileStepListener,
            ErrorPerChunkListener errorPerChunkListener) {

        return new StepBuilder("step_default_ip_block", jobRepository)
                .<RawLineData, DefaultIpBlockDto>chunk(ipChunkSize)
                .transactionManager(transactionManager)
                .reader(reader)
                .processor(
                        new GenericFieldMapper<>(
                                new DefaultIpBlockCsvReader(), batchFileDetailRepository))
                .writer(defaultIpBlockWriter)
                .listener(new ClaimStepInfoForReaderListener(DefaultFileType.IP_BLOCK, ipChunkSize))
                .listener(fileStepListener)
                .listener(errorPerChunkListener)
                .build();
    }

    /**
     * Assembles the {@code fileImportJob} Spring Batch job for the default data provider.
     *
     * <p>The job runs two steps sequentially:
     *
     * <ol>
     *   <li>{@code defaultLocationStep} — imports flat location records (continent, country,
     *       subdivision, city in one row)
     *   <li>{@code defaultIpBlockStep} — imports IP block ranges
     * </ol>
     *
     * <p>Both steps continue regardless of exit status ({@code COMPLETED}, {@code NOOP}, {@code
     * FAILED}, etc.) so that a failure in one step does not prevent the other from running.
     *
     * @param defaultLocationStep imports location records
     * @param defaultIpBlockStep imports IP block ranges
     * @param jobListener lifecycle listener for the job
     * @return the configured {@code fileImportJob}
     */
    @Bean
    public Job fileImportJob(
            Step defaultLocationStep, Step defaultIpBlockStep, JobListener jobListener) {

        Flow mainFlow =
                new FlowBuilder<Flow>("defaultMainFlow")
                        .start(defaultLocationStep)
                        .on("*")
                        .to(defaultIpBlockStep)
                        .build();

        return new JobBuilder(FileImportService.FILE_IMPORT_JOB_NAME, jobRepository)
                .listener(jobListener)
                .start(mainFlow)
                .build()
                .build();
    }
}
