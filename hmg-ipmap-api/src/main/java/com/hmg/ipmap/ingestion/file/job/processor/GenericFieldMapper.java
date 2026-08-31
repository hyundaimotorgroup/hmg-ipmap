package com.hmg.ipmap.ingestion.file.job.processor;

import com.hmg.ipmap.ingestion.file.job.error.ErrorCollector;
import com.hmg.ipmap.ingestion.file.job.reader.AbstractCsvReader;
import com.hmg.ipmap.ingestion.file.job.reader.RawLineData;
import com.hmg.ipmap.ingestion.file.repository.BatchFileDetailRepository;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ItemProcessor;

@Slf4j
public record GenericFieldMapper<T>(
        AbstractCsvReader<T> reader, BatchFileDetailRepository batchFileDetailRepository)
        implements ItemProcessor<RawLineData, T>, StepExecutionListener {

    private static final String ERROR_MESSAGE = "CSV data does not match expected format";

    @Override
    public T process(RawLineData rawLineData) {
        try {
            boolean isSafeLine = reader.validateLine(rawLineData.lineData());
            if (!isSafeLine) {
                ErrorCollector.add(rawLineData.id(), ERROR_MESSAGE);
                return null;
            }
            T result = reader.readLine(rawLineData.lineData(), rawLineData.id());
            if (result == null) {
                ErrorCollector.add(rawLineData.id(), ERROR_MESSAGE);
            }
            return result;
        } catch (IndexOutOfBoundsException e) {
            ErrorCollector.add(rawLineData.id(), ERROR_MESSAGE);
            return null;
        }
    }

    @Override
    public ExitStatus afterStep(@NonNull StepExecution stepExecution) {
        reader.cleanup();
        return stepExecution.getExitStatus();
    }
}
