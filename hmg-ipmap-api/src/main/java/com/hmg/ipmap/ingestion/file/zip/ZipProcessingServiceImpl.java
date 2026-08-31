package com.hmg.ipmap.ingestion.file.zip;

import com.hmg.ipmap.common.exception.ConflictException;
import com.hmg.ipmap.common.exception.NotFoundException;
import com.hmg.ipmap.ingestion.config.IngestionUploadProperties;
import com.hmg.ipmap.ingestion.file.csv.CsvProcessingService;
import com.hmg.ipmap.ingestion.file.entity.BatchFileEntity;
import com.hmg.ipmap.ingestion.file.entity.BatchFileStatusEnum;
import com.hmg.ipmap.ingestion.file.entity.BatchFileZipEntity;
import com.hmg.ipmap.ingestion.file.entity.BatchRunEntity;
import com.hmg.ipmap.ingestion.file.entity.BatchRunStatusEnum;
import com.hmg.ipmap.ingestion.file.enums.FileType;
import com.hmg.ipmap.ingestion.file.enums.ZipStatusEnum;
import com.hmg.ipmap.ingestion.file.repository.BatchFileRepository;
import com.hmg.ipmap.ingestion.file.repository.BatchFileZipRepository;
import com.hmg.ipmap.ingestion.file.repository.BatchRunRepository;
import com.hmg.ipmap.ingestion.file.zip.exception.ZipEntryLimitExceededException;
import com.hmg.ipmap.ingestion.file.zip.validator.ZipSecurityValidator;
import com.hmg.ipmap.ingestion.provider.DataProvider;
import com.hmg.ipmap.ingestion.provider.ImportType;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongUnaryOperator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Abstract base for ZIP file processing. Coordinates extraction, validation, and CSV processing
 * workflows. Subclasses implement {@link #postProcess(BatchFileZipEntity)} to perform any
 * provider-specific logic after extraction completes.
 */
@Slf4j
@RequiredArgsConstructor
public abstract class ZipProcessingServiceImpl implements ZipProcessingService {

    private final IngestionUploadProperties uploadProperties;

    protected final BatchFileRepository batchFileRepository;
    private final BatchFileZipRepository batchFileZipRepository;
    private final CsvProcessingService csvProcessingService;
    private final BatchRunRepository batchRunRepository;
    private final ZipThresholdProperties zipThresholdProperties;
    private final ZipSecurityValidator securityValidator;
    private final DataProvider dataProvider;

    /**
     * Called after all CSV files have been extracted and their lines stored. Subclasses can
     * override this to perform provider-specific post-extraction logic.
     */
    protected abstract void postProcess(BatchFileZipEntity zip);

    @Override
    public void extractZip(Long fileZipId) {
        BatchFileZipEntity zip =
                batchFileZipRepository
                        .findById(fileZipId)
                        .orElseThrow(() -> new NotFoundException("Zip file not found"));

        if (!ZipStatusEnum.INIT.equals(zip.getStatus())) {
            throw new ConflictException("Zip already executed");
        }

        onStartExtracting(zip);

        try (ZipInputStream zipInputStream =
                new ZipInputStream(new FileInputStream(zip.getPath()))) {
            processZipEntries(zipInputStream, zip);
            finalizeExtraction(zip);

            onEndExtracting(zip);
        } catch (Exception e) {
            log.error("Error when extracting zip", e);
            handleExtractionFailure(zip, e);
        } finally {
            cleanupZipFile(zip);
        }
    }

    /** Processes all entries in the ZIP archive. */
    private void processZipEntries(ZipInputStream zipInputStream, BatchFileZipEntity zip)
            throws IOException {
        Path targetDir = Paths.get(uploadProperties.getFolder()).toRealPath();
        ImportType importType = dataProvider.resolveImportType(zip.getZipType());
        ZipEntry entry;
        long totalEntryCount = 0;
        AtomicLong totalSizeArchive = new AtomicLong();

        while ((entry = zipInputStream.getNextEntry()) != null) {
            Path validatedPath = validateAndPreparePath(entry, targetDir, ++totalEntryCount);

            if (isCsvFile(entry)) {
                Optional<FileType> fileTypeEnum =
                        dataProvider.detectFileType(importType, entry.getName());
                if (fileTypeEnum.isEmpty()) {
                    log.warn("Invalid file name: {}", entry.getName());
                    continue;
                }

                Path extractedPath =
                        extractFile(
                                zipInputStream, entry, validatedPath, totalSizeArchive::addAndGet);
                saveBatchFile(zip, fileTypeEnum.get(), extractedPath);
            }
            zipInputStream.closeEntry();
        }
    }

    /** Validates ZIP entry and prepares extraction path. */
    private Path validateAndPreparePath(ZipEntry entry, Path targetDir, long totalEntryCount)
            throws IOException {
        String entryName = entry.getName();

        // Validate filename
        securityValidator.validateFilename(entryName);

        // Validate path traversal (Zip Slip)
        Path validatedPath = securityValidator.validateZipSlip(entryName, targetDir);

        // Validate symbolic links
        securityValidator.validateNoSymlinkEscape(validatedPath, targetDir);

        // Validate entry count limit
        if (totalEntryCount > zipThresholdProperties.getEntries()) {
            throw new ZipEntryLimitExceededException("Too many entries in ZIP file");
        }

        return validatedPath;
    }

    private boolean isCsvFile(ZipEntry entry) {
        return !entry.isDirectory() && entry.getName().trim().toLowerCase().endsWith(".csv");
    }

    private void finalizeExtraction(BatchFileZipEntity zip) {
        List<BatchFileEntity> batchFiles = batchFileRepository.findAllByBatchFileZip(zip);
        batchFiles.forEach(
                batchFile -> {
                    markBatchFileAsUploading(batchFile);
                    csvProcessingService.readCsvAndStoreLines(batchFile);
                });

        zip.setStatus(ZipStatusEnum.EXTRACTED);
        batchFileZipRepository.save(zip);
    }

    private void handleExtractionFailure(BatchFileZipEntity zip, Exception e) {
        log.error("Failed to process ZIP file", e);
        zip.setStatus(ZipStatusEnum.FAILED);
        zip.setErrorMessage(e.getMessage());
        batchFileZipRepository.save(zip);

        changeStatus(zip.getBatchRun(), BatchRunStatusEnum.RECEIVED);
    }

    private void onStartExtracting(BatchFileZipEntity zip) {
        changeStatus(zip.getBatchRun(), BatchRunStatusEnum.UPLOADING);
    }

    private void onEndExtracting(BatchFileZipEntity zip) {
        postProcess(zip);
        changeStatus(zip.getBatchRun(), BatchRunStatusEnum.READY);
    }

    private void changeStatus(BatchRunEntity batchRun, BatchRunStatusEnum status) {
        batchRun.setStatus(status);
        batchRunRepository.save(batchRun);
    }

    private void saveBatchFile(BatchFileZipEntity zip, FileType fileType, Path filePath) {
        BatchFileEntity batchFile = new BatchFileEntity();
        batchFile.setBatchFileZip(zip);
        batchFile.setBatchRun(zip.getBatchRun());
        batchFile.setFileName(filePath.getFileName().toString());
        batchFile.setFileType(fileType.name());
        batchFile.setStatus(BatchFileStatusEnum.INIT);
        batchFile.setPath(filePath.toString());
        batchFileRepository.save(batchFile);
    }

    private void markBatchFileAsUploading(BatchFileEntity batchFile) {
        batchFile.setStatus(BatchFileStatusEnum.UPLOADING);
        batchFileRepository.save(batchFile);
    }

    private Path extractFile(
            InputStream entryStream,
            ZipEntry entry,
            Path validatedPath,
            LongUnaryOperator accumulateBytes)
            throws IOException {

        log.info("Extracting file: {}", entry.getName());

        Path normalizedPath = validatedPath.normalize();
        securityValidator.validateFileNotExists(normalizedPath);
        Files.createDirectories(normalizedPath.getParent());

        long totalSizeEntry = 0;
        try (OutputStream os = Files.newOutputStream(normalizedPath)) {
            byte[] buffer = new byte[8192];
            int nBytes;
            while ((nBytes = entryStream.read(buffer)) > 0) {
                os.write(buffer, 0, nBytes);
                totalSizeEntry += nBytes;

                securityValidator.validateEntrySizeLimit(totalSizeEntry);
                long totalSizeArchive = accumulateBytes.applyAsLong(nBytes);
                securityValidator.checkZipBomb(entry, totalSizeEntry, totalSizeArchive);
            }
        }

        log.info("File {} extracted with size {}", entry.getName(), totalSizeEntry);
        return normalizedPath;
    }

    private void cleanupZipFile(BatchFileZipEntity zip) {
        try {
            Path zipPath = Paths.get(zip.getPath());
            if (Files.deleteIfExists(zipPath)) {
                log.debug("Deleted ZIP file: {}", zipPath);
            }
        } catch (IOException e) {
            log.warn("Failed to delete ZIP file: {}", zip.getPath(), e);
        }
    }
}
