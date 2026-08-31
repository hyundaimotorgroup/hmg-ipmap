package com.hmg.ipmap.ingestion.file.csv;

import com.hmg.ipmap.common.context.UserContextHolder;
import com.hmg.ipmap.common.exception.InternalServerErrorException;
import com.hmg.ipmap.ingestion.config.IngestionUploadProperties;
import com.hmg.ipmap.ingestion.file.entity.BatchFileDetailEntity;
import com.hmg.ipmap.ingestion.file.entity.BatchFileEntity;
import com.hmg.ipmap.ingestion.file.entity.BatchFileStatusEnum;
import com.hmg.ipmap.ingestion.file.enums.BatchFileDetailStatusEnum;
import com.hmg.ipmap.ingestion.file.repository.BatchFileDetailRepository;
import com.hmg.ipmap.ingestion.file.repository.BatchFileRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CsvProcessingServiceImpl implements CsvProcessingService {

    private final BatchFileRepository batchFileRepository;
    private final BatchFileDetailRepository batchFileDetailRepository;
    private final IngestionUploadProperties uploadProperties;

    @Override
    public void readCsvAndStoreLines(BatchFileEntity batchFile) {
        log.trace(
                "Save to batch file detail table for file: {}, {}",
                batchFile.getId(),
                batchFile.getFileName());

        try (BufferedReader reader = Files.newBufferedReader(Paths.get(batchFile.getPath()))) {
            /* trigger to skip header */
            String header = reader.readLine();
            log.trace("Read line with header {}", header);

            String line;
            int rawLineNo = 0;
            int skippedCount = 0;
            List<BatchFileDetailEntity> pending = new ArrayList<>();

            while ((line = reader.readLine()) != null) {
                rawLineNo++;
                BatchFileDetailEntity fileDetail = new BatchFileDetailEntity();
                fileDetail.setLineNo(rawLineNo);
                fileDetail.setLineData(line);
                fileDetail.setLineHash(LineHashUtil.compute(line));
                fileDetail.setStatus(BatchFileDetailStatusEnum.INIT);
                fileDetail.setBatchFile(batchFile);
                fileDetail.setCreatedAt(Instant.now());
                fileDetail.setCreatedBy(UserContextHolder.get().id());
                pending.add(fileDetail);

                if (pending.size() >= uploadProperties.getInsertBatchSize()) {
                    int saved = filterAndSave(pending);
                    skippedCount += pending.size() - saved;
                    pending.clear();
                }
            }

            if (!pending.isEmpty()) {
                int saved = filterAndSave(pending);
                skippedCount += pending.size() - saved;
            }

            if (skippedCount > 0) {
                log.info(
                        "Skipped {} duplicate line(s) for file: {} (id={})",
                        skippedCount,
                        batchFile.getFileName(),
                        batchFile.getId());
            }

            batchFile.setStatus(BatchFileStatusEnum.READY);
            batchFile.setLineCount(rawLineNo);
            batchFile.setSkipCount(skippedCount);
            batchFileRepository.save(batchFile);
        } catch (IOException e) {
            log.error("error save to batch_file_detail", e);
            handleError(batchFile, e.getMessage());
            throw new InternalServerErrorException(e.getMessage());
        } finally {
            try {
                Files.delete(Paths.get(batchFile.getPath()));
            } catch (IOException e) {
                log.warn("failed to delete the file", e);
            }
        }
    }

    /**
     * Deduplicates the pending batch (intra-chunk) then persists new lines and promotes existing
     * ones.
     *
     * <p>Intra-chunk duplicates (same hash within one chunk) are discarded — only the first
     * occurrence is kept. Of the remaining unique lines:
     *
     * <ul>
     *   <li>Lines whose hash <em>does not</em> exist in the DB are inserted with status {@code
     *       INIT}.
     *   <li>Lines whose hash <em>already</em> exists in the DB are not inserted; instead, the
     *       existing records are updated to status {@code PROMOTE} so downstream processing can
     *       extend the validity of the corresponding {@code ip_mapping} entries.
     * </ul>
     *
     * @return the number of inserted lines in this chunk; the caller uses this to compute the
     *     intra-chunk discard count stored in {@code skip_count}
     */
    private int filterAndSave(List<BatchFileDetailEntity> pending) {
        // Intra-chunk dedup: keep first occurrence per hash
        Map<UUID, BatchFileDetailEntity> unique = new LinkedHashMap<>();
        for (BatchFileDetailEntity e : pending) {
            unique.putIfAbsent(e.getLineHash(), e);
        }

        // Inter-file dedup: single DB query for existing hashes
        Set<UUID> existingHashes = batchFileDetailRepository.findExistingHashes(unique.keySet());

        List<BatchFileDetailEntity> toSave =
                unique.values().stream()
                        .filter(e -> !existingHashes.contains(e.getLineHash()))
                        .toList();

        if (!toSave.isEmpty()) {
            batchFileDetailRepository.saveAllInBatch(toSave);
        }
        if (!existingHashes.isEmpty()) {
            batchFileDetailRepository.updateAllToPromoteByHashes(existingHashes);
            log.debug(
                    "Marked {} existing record(s) as PROMOTE for validity extension",
                    existingHashes.size());
        }
        return toSave.size();
    }

    private void handleError(BatchFileEntity batchFile, String errorMessage) {
        batchFile.setStatus(BatchFileStatusEnum.FAILED);
        batchFile.setErrorMessage(errorMessage);
        batchFileRepository.save(batchFile);
    }
}
