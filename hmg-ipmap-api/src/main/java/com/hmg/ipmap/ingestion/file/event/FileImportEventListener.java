package com.hmg.ipmap.ingestion.file.event;

import com.hmg.ipmap.ingestion.file.zip.ZipProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileImportEventListener {

    private final ZipProcessingService zipProcessingService;

    /**
     * Handles an {@link UploadEvent} after the publishing transaction has committed.
     *
     * <p>Runs asynchronously on the {@code applicationTaskExecutor} thread pool so that ZIP
     * extraction does not block the upload request thread. The listener is bound to {@link
     * TransactionPhase#AFTER_COMMIT} to guarantee the {@code BatchFileZipEntity} row is visible to
     * the extraction logic before processing begins.
     *
     * @param event the upload event carrying the ID of the persisted {@code BatchFileZipEntity}
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("fileImportTaskExecutor")
    public void onUpload(UploadEvent event) {
        log.info("Processing upload event for file zip ID: {}", event.getFileZipId());
        zipProcessingService.extractZip(event.getFileZipId());
    }
}
