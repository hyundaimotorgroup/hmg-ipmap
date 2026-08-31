package com.hmg.ipmap.ingestion.file.job.reader;

import com.hmg.ipmap.ingestion.file.repository.BatchFileDetailRepository;
import java.util.Iterator;
import java.util.List;
import org.springframework.batch.infrastructure.item.ItemReader;

public class ClaimingItemReader implements ItemReader<RawLineData> {

    private Iterator<RawLineData> buffer;
    private final long batchId;
    private final String fileType;
    private final int claimedSize;
    private final BatchFileDetailRepository batchFileDetailRepository;

    public ClaimingItemReader(
            BatchFileDetailRepository batchFileDetailRepository,
            Long batchId,
            String fileType,
            Integer claimedSize) {
        if (batchId == null) {
            throw new IllegalArgumentException(
                    "batchId cannot be null. register ClaimStepInfoForReaderListener");
        }
        if (fileType == null) {
            throw new IllegalArgumentException(
                    "fileType cannot be null. register ClaimStepInfoForReaderListener");
        }
        if (claimedSize == null || claimedSize <= 0) {
            throw new IllegalArgumentException(
                    "claimedSize must be greater than 0. register ClaimStepInfoForReaderListener");
        }
        this.batchFileDetailRepository = batchFileDetailRepository;
        this.batchId = batchId;
        this.fileType = fileType;
        this.claimedSize = claimedSize;
    }

    @Override
    public RawLineData read() {
        if (buffer == null || !buffer.hasNext()) {
            List<RawLineData> claimed =
                    batchFileDetailRepository.claimForProcess(batchId, fileType, claimedSize);
            if (claimed.isEmpty()) {
                return null;
            }
            buffer = claimed.iterator();
        }
        return buffer.next();
    }
}
