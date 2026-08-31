package com.hmg.ipmap.ingestion.file.job.listener;

import com.hmg.ipmap.common.context.UserContextHolder;
import com.hmg.ipmap.ingestion.file.entity.BatchFileEntity;
import com.hmg.ipmap.ingestion.file.entity.BatchFileStatusEnum;
import com.hmg.ipmap.ingestion.file.job.JobParameter;
import com.hmg.ipmap.ingestion.file.repository.BatchFileRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

@Slf4j
@AllArgsConstructor
@Component
public class FileStepListener implements StepExecutionListener {

    static final String FILE_TYPE_KEY = "fileType";

    private BatchFileRepository batchFileRepository;
    private JobParameter jobParameter;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        log.info("before step: {}", stepExecution.getStepName());

        if (stepExecution.getExecutionContext().containsKey(FILE_TYPE_KEY)) {
            String fileType = stepExecution.getExecutionContext().getString(FILE_TYPE_KEY);
            markToInProgress(jobParameter.getBatchId(), fileType);
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        log.info("after step: {}", stepExecution.getStepName());

        if (stepExecution.getExecutionContext().containsKey(FILE_TYPE_KEY)) {
            String fileType = stepExecution.getExecutionContext().getString(FILE_TYPE_KEY);
            Long batchId = jobParameter.getBatchId();
            ExitStatus exitStatus = stepExecution.getExitStatus();
            String exitMessage = exitStatus.getExitDescription();
            if (!ExitStatus.COMPLETED.equals(exitStatus)) {
                exitMessage =
                        String.format(
                                "Step '%s' did not complete successfully. Exit status: %s, message: %s",
                                stepExecution.getStepName(),
                                exitStatus.getExitCode(),
                                exitStatus.getExitDescription());
                log.warn(exitMessage);
                // stop the whole process if any exception occurs in the step process
                stepExecution.getJobExecution().setStatus(BatchStatus.STOPPING);
            }
            BatchFileStatusEnum result =
                    ExitStatus.COMPLETED.equals(exitStatus)
                            ? BatchFileStatusEnum.COMPLETED
                            : BatchFileStatusEnum.FAILED;
            markToFinished(batchId, fileType, result, exitMessage);
        }
        return stepExecution.getExitStatus();
    }

    private void markToInProgress(Long batchId, String fileType) {
        List<BatchFileEntity> batchFiles =
                batchFileRepository.findAllByBatchRunIdAndFileType(batchId, fileType);
        batchFiles.forEach(
                batchFile -> {
                    batchFile.setStatus(BatchFileStatusEnum.IN_PROGRESS);
                    batchFile.setProcessedAt(LocalDateTime.now());
                    batchFile.setUpdatedAt(Instant.now());
                    batchFile.setUpdatedBy(UserContextHolder.get().id());
                });

        batchFileRepository.saveAll(batchFiles);
    }

    private void markToFinished(
            Long batchId, String fileType, BatchFileStatusEnum status, String errorMessage) {
        List<BatchFileEntity> batchFiles =
                batchFileRepository.findAllByBatchRunIdAndFileType(batchId, fileType);
        batchFiles.forEach(
                batchFile -> {
                    batchFile.setStatus(status);
                    batchFile.setProcessedAt(LocalDateTime.now());
                    batchFile.setUpdatedAt(Instant.now());
                    batchFile.setUpdatedBy(UserContextHolder.get().id());
                    batchFile.setErrorMessage(errorMessage);
                });

        batchFileRepository.saveAll(batchFiles);
    }
}
