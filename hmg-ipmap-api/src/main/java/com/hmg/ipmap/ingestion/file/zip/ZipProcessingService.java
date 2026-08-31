package com.hmg.ipmap.ingestion.file.zip;

import com.hmg.ipmap.ingestion.file.entity.BatchFileZipEntity;

/**
 * Main orchestration service for ZIP file processing. Coordinates extraction, validation, and CSV
 * processing workflows.
 */
public interface ZipProcessingService {

    /**
     * Extracts and processes the uploaded ZIP file identified by its database record ID.
     *
     * @param fileZipId the primary key of the {@link BatchFileZipEntity} to process
     * @throws com.hmg.ipmap.common.exception.NotFoundException if no ZIP record exists for the
     *     given ID
     * @throws com.hmg.ipmap.common.exception.ConflictException if the ZIP has already been
     *     processed
     */
    void extractZip(Long fileZipId);
}
