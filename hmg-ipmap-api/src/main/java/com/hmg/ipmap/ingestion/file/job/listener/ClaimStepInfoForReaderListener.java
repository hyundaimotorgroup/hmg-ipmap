package com.hmg.ipmap.ingestion.file.job.listener;

import com.hmg.ipmap.ingestion.file.enums.FileType;
import org.jspecify.annotations.NonNull;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;

public class ClaimStepInfoForReaderListener implements StepExecutionListener {

    private final String fileType;
    private final int claimSize;

    public ClaimStepInfoForReaderListener(FileType fileType, int claimSize) {
        if (fileType == null) {
            throw new NullPointerException("fileType is not set");
        }
        if (claimSize <= 0) {
            throw new IllegalArgumentException("invalid claim size");
        }
        this.fileType = fileType.name();
        this.claimSize = claimSize;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        stepExecution.getExecutionContext().put(FileStepListener.FILE_TYPE_KEY, fileType);
        stepExecution.getExecutionContext().put("claimSize", claimSize);
    }

    @Override
    public ExitStatus afterStep(@NonNull StepExecution stepExecution) {
        return ExitStatus.COMPLETED;
    }
}
