package com.hmg.ipmap.ingestion.file.csv;

import com.hmg.ipmap.ingestion.file.entity.BatchFileDetailEntity;
import com.hmg.ipmap.ingestion.file.entity.BatchFileEntity;
import com.hmg.ipmap.ingestion.file.entity.BatchFileStatusEnum;

public interface CsvProcessingService {
    /**
     * Reads the CSV file referenced by the given {@link BatchFileEntity}, persists each data row as
     * a {@link BatchFileDetailEntity}, and marks the file as {@link BatchFileStatusEnum#READY} with
     * its total line count on success. The temporary file is always deleted from disk regardless of
     * outcome.
     *
     * @param batchFile the batch file entity whose path points to the extracted CSV file
     * @throws com.hmg.ipmap.common.exception.InternalServerErrorException if the CSV file cannot be
     *     read
     */
    void readCsvAndStoreLines(BatchFileEntity batchFile);
}
