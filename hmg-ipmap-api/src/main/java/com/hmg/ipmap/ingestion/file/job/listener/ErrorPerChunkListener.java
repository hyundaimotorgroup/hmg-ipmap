package com.hmg.ipmap.ingestion.file.job.listener;

import com.hmg.ipmap.ingestion.file.job.error.ErrorCollector;
import com.hmg.ipmap.ingestion.file.job.error.FileDetailError;
import com.hmg.ipmap.ingestion.file.repository.BatchFileDetailRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.batch.core.listener.ChunkListener;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ErrorPerChunkListener implements ChunkListener<Object, Object> {

    private final BatchFileDetailRepository batchFileDetailRepository;

    @Override
    public void beforeChunk(@NonNull Chunk<Object> chunk) {
        ErrorCollector.startChunk();
    }

    @Override
    public void afterChunk(@NonNull Chunk<Object> chunk) {
        ErrorCollector.drain(this::flush);
    }

    private void flush(List<FileDetailError> errors) {
        if (errors == null || errors.isEmpty()) return;
        log.info("{} Errors found", errors.size());
        batchFileDetailRepository.updateAllToErrorInBatch(errors);
    }
}
