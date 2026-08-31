package com.hmg.ipmap.ingestion.file.zip;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hmg.ipmap.common.exception.ConflictException;
import com.hmg.ipmap.common.exception.NotFoundException;
import com.hmg.ipmap.ingestion.config.IngestionUploadProperties;
import com.hmg.ipmap.ingestion.file.csv.CsvProcessingService;
import com.hmg.ipmap.ingestion.file.entity.BatchFileEntity;
import com.hmg.ipmap.ingestion.file.entity.BatchFileZipEntity;
import com.hmg.ipmap.ingestion.file.entity.BatchRunEntity;
import com.hmg.ipmap.ingestion.file.enums.FileType;
import com.hmg.ipmap.ingestion.file.enums.ZipStatusEnum;
import com.hmg.ipmap.ingestion.file.repository.BatchFileRepository;
import com.hmg.ipmap.ingestion.file.repository.BatchFileZipRepository;
import com.hmg.ipmap.ingestion.file.repository.BatchRunRepository;
import com.hmg.ipmap.ingestion.file.zip.exception.InvalidZipEntryNameException;
import com.hmg.ipmap.ingestion.file.zip.exception.ZipBombException;
import com.hmg.ipmap.ingestion.file.zip.exception.ZipSlipException;
import com.hmg.ipmap.ingestion.file.zip.validator.ZipSecurityValidator;
import com.hmg.ipmap.ingestion.provider.DataProvider;
import com.hmg.ipmap.ingestion.provider.ImportType;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;

@ExtendWith(MockitoExtension.class)
class ZipProcessingServiceTest {

    @InjectMocks private DefaultZipProcessingServiceImpl zipProcessingService;

    @Mock private BatchFileZipRepository batchFileZipRepository;

    @Mock private CsvProcessingService csvProcessingService;

    @Mock private BatchFileRepository batchFileRepository;

    @Mock private BatchRunRepository batchRunRepository;

    @Mock private ZipThresholdProperties zipThresholdProperties;

    @Mock private ZipSecurityValidator zipSecurityValidator;

    @Mock private DataProvider dataProvider;

    @Mock private IngestionUploadProperties uploadProperties;

    @Mock private ImportType mockImportType;

    @Mock private FileType mockFileType;

    @BeforeEach
    void setUp() throws IOException {
        // Create upload directory for tests
        Path uploadDir = Paths.get(System.getProperty("java.io.tmpdir"), "test-upload");
        Files.createDirectories(uploadDir);

        lenient().when(uploadProperties.getFolder()).thenReturn(uploadDir.toString());

        // Configure ZipThresholdProperties mock (lenient for flexibility across different tests)
        lenient().when(zipThresholdProperties.getEntries()).thenReturn(20);
        lenient()
                .when(zipThresholdProperties.getSize())
                .thenReturn(DataSize.of(1, DataUnit.GIGABYTES));
        lenient()
                .when(zipThresholdProperties.getEntrySize())
                .thenReturn(DataSize.of(300, DataUnit.MEGABYTES));
        lenient().when(zipThresholdProperties.getRatio()).thenReturn(10L);

        // Configure ZipSecurityValidator mock (lenient to allow all tests to pass by default)
        lenient()
                .when(zipSecurityValidator.validateZipSlip(any(String.class), any(Path.class)))
                .thenAnswer(
                        invocation -> {
                            String entryName = invocation.getArgument(0);
                            Path targetDir = invocation.getArgument(1);
                            return targetDir.resolve(entryName).normalize();
                        });
        lenient().doNothing().when(zipSecurityValidator).validateFilename(any(String.class));
        lenient()
                .doNothing()
                .when(zipSecurityValidator)
                .validateNoSymlinkEscape(any(Path.class), any(Path.class));
        lenient().doNothing().when(zipSecurityValidator).validateFileNotExists(any(Path.class));
        lenient().doNothing().when(zipSecurityValidator).validateEntrySizeLimit(anyLong());
        lenient()
                .doNothing()
                .when(zipSecurityValidator)
                .checkZipBomb(any(ZipEntry.class), anyLong(), anyLong());

        // Configure DataProvider mock (lenient default: resolve any zip type and detect as
        // COUNTRY_LOCATION)
        lenient().when(dataProvider.resolveImportType(any())).thenReturn(mockImportType);
        lenient()
                .when(dataProvider.detectFileType(any(), any(String.class)))
                .thenReturn(Optional.of(mockFileType));
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up test directories
        Path uploadDir = Paths.get(System.getProperty("java.io.tmpdir"), "test-upload");
        Path secureTempDir = Paths.get(System.getProperty("java.io.tmpdir"), "hmg-ip-map");

        deleteDirectoryRecursively(uploadDir);
        deleteDirectoryRecursively(secureTempDir);
    }

    private void deleteDirectoryRecursively(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (Stream<Path> paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(
                                path -> {
                                    try {
                                        Files.delete(path);
                                    } catch (IOException _) {
                                        // Ignore cleanup errors in tests
                                    }
                                });
            }
        }
    }

    @Test
    void shouldThrowExceptionWhenZipFileNotFound() {
        Long fileZipId = 1L;
        when(batchFileZipRepository.findById(fileZipId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> zipProcessingService.extractZip(fileZipId));
    }

    @Test
    void shouldThrowExceptionWhenZipAlreadyExecuted() {
        Long fileZipId = 1L;
        BatchFileZipEntity zipEntity = new BatchFileZipEntity();
        zipEntity.setStatus(ZipStatusEnum.EXTRACTED);
        when(batchFileZipRepository.findById(fileZipId)).thenReturn(Optional.of(zipEntity));

        assertThrows(ConflictException.class, () -> zipProcessingService.extractZip(fileZipId));
    }

    @Test
    void shouldExtractCsvFilesAndUpdateStatus() throws IOException {
        Long fileZipId = 1L;
        File tempZip = createTempZipWithCsv();

        BatchFileZipEntity zipEntity = new BatchFileZipEntity();
        zipEntity.setId(fileZipId);
        zipEntity.setPath(tempZip.getAbsolutePath());
        zipEntity.setStatus(ZipStatusEnum.INIT);
        zipEntity.setBatchRun(new BatchRunEntity());

        when(batchFileZipRepository.findById(fileZipId)).thenReturn(Optional.of(zipEntity));

        BatchFileEntity mockBatchFile1 = new BatchFileEntity();
        mockBatchFile1.setFileName("country-ipblocks.csv");
        mockBatchFile1.setPath("/tmp/country-ipblocks.csv");

        BatchFileEntity mockBatchFile2 = new BatchFileEntity();
        mockBatchFile2.setFileName("city-ipblocks.csv");
        mockBatchFile2.setPath("/tmp/city-ipblocks.csv");

        when(batchFileRepository.findAllByBatchFileZip(zipEntity))
                .thenReturn(List.of(mockBatchFile1, mockBatchFile2));

        zipProcessingService.extractZip(fileZipId);

        verify(batchFileZipRepository, atLeastOnce()).save(zipEntity);
        verify(csvProcessingService, atLeastOnce())
                .readCsvAndStoreLines(any(BatchFileEntity.class));
        verify(batchRunRepository, atLeastOnce()).save(any(BatchRunEntity.class));
    }

    @Test
    void shouldHandleIOException() {
        Long fileZipId = 1L;
        BatchFileZipEntity zipEntity = new BatchFileZipEntity();
        zipEntity.setId(fileZipId);
        zipEntity.setPath("invalid/path/to.zip");
        zipEntity.setStatus(ZipStatusEnum.INIT);
        zipEntity.setBatchRun(new BatchRunEntity());

        when(batchFileZipRepository.findById(fileZipId)).thenReturn(Optional.of(zipEntity));

        zipProcessingService.extractZip(fileZipId);

        verify(batchFileZipRepository, atLeastOnce())
                .save(
                        argThat(
                                zip ->
                                        zip.getStatus() == ZipStatusEnum.FAILED
                                                && zip.getErrorMessage() != null));
    }

    @Test
    void shouldHandleZipSlipDetected() throws IOException {
        Long fileZipId = 1L;
        File tempZip = createMaliciousZipWithPathTraversal();

        BatchFileZipEntity zipEntity = new BatchFileZipEntity();
        zipEntity.setId(fileZipId);
        zipEntity.setPath(tempZip.getAbsolutePath());
        zipEntity.setStatus(ZipStatusEnum.INIT);
        zipEntity.setBatchRun(new BatchRunEntity());

        when(batchFileZipRepository.findById(fileZipId)).thenReturn(Optional.of(zipEntity));

        // Mock validator to throw exception for path traversal
        when(zipSecurityValidator.validateZipSlip(contains("../"), any(Path.class)))
                .thenThrow(new ZipSlipException("Invalid entry: passwd.csv"));

        zipProcessingService.extractZip(fileZipId);

        verify(batchFileZipRepository, atLeastOnce())
                .save(
                        argThat(
                                zip ->
                                        zip.getStatus() == ZipStatusEnum.FAILED
                                                && zip.getErrorMessage() != null
                                                && zip.getErrorMessage()
                                                        .contains("Invalid entry")));
    }

    @Test
    void shouldHandleTooManyEntries() throws IOException {
        Long fileZipId = 1L;
        when(zipThresholdProperties.getEntries()).thenReturn(2); // Lower limit for testing

        File tempZip = createZipWithManyEntries();

        BatchFileZipEntity zipEntity = new BatchFileZipEntity();
        zipEntity.setId(fileZipId);
        zipEntity.setPath(tempZip.getAbsolutePath());
        zipEntity.setStatus(ZipStatusEnum.INIT);
        zipEntity.setBatchRun(new BatchRunEntity());

        when(batchFileZipRepository.findById(fileZipId)).thenReturn(Optional.of(zipEntity));

        zipProcessingService.extractZip(fileZipId);

        verify(batchFileZipRepository, atLeastOnce())
                .save(
                        argThat(
                                zip ->
                                        zip.getStatus() == ZipStatusEnum.FAILED
                                                && zip.getErrorMessage() != null
                                                && zip.getErrorMessage()
                                                        .contains("Too many entries")));
    }

    @Test
    void shouldHandleFilenameContainsNullByte() throws IOException {
        Long fileZipId = 1L;
        File tempZip = createZipWithNullByteFilename();

        BatchFileZipEntity zipEntity = new BatchFileZipEntity();
        zipEntity.setId(fileZipId);
        zipEntity.setPath(tempZip.getAbsolutePath());
        zipEntity.setStatus(ZipStatusEnum.INIT);
        zipEntity.setBatchRun(new BatchRunEntity());

        when(batchFileZipRepository.findById(fileZipId)).thenReturn(Optional.of(zipEntity));

        // Mock validator to throw exception for null byte in filename
        doThrow(new InvalidZipEntryNameException("Filename contains null byte characters"))
                .when(zipSecurityValidator)
                .validateFilename(contains("\0"));

        zipProcessingService.extractZip(fileZipId);

        verify(batchFileZipRepository, atLeastOnce())
                .save(
                        argThat(
                                zip ->
                                        zip.getStatus() == ZipStatusEnum.FAILED
                                                && zip.getErrorMessage() != null
                                                && zip.getErrorMessage().contains("null byte")));
    }

    @Test
    void shouldHandleFilenameUsesWindowsReservedName() throws IOException {
        Long fileZipId = 1L;
        File tempZip = createZipWithReservedFilename();

        BatchFileZipEntity zipEntity = new BatchFileZipEntity();
        zipEntity.setId(fileZipId);
        zipEntity.setPath(tempZip.getAbsolutePath());
        zipEntity.setStatus(ZipStatusEnum.INIT);
        zipEntity.setBatchRun(new BatchRunEntity());

        when(batchFileZipRepository.findById(fileZipId)).thenReturn(Optional.of(zipEntity));

        // Mock validator to throw exception for reserved filename
        doThrow(new InvalidZipEntryNameException("Filename uses reserved name: CON"))
                .when(zipSecurityValidator)
                .validateFilename("CON.csv");

        zipProcessingService.extractZip(fileZipId);

        verify(batchFileZipRepository, atLeastOnce())
                .save(
                        argThat(
                                zip ->
                                        zip.getStatus() == ZipStatusEnum.FAILED
                                                && zip.getErrorMessage() != null
                                                && zip.getErrorMessage()
                                                        .contains("reserved name")));
    }

    @Test
    void shouldHandleFilenameTooLong() throws IOException {
        Long fileZipId = 1L;
        File tempZip = createZipWithLongFilename();

        BatchFileZipEntity zipEntity = new BatchFileZipEntity();
        zipEntity.setId(fileZipId);
        zipEntity.setPath(tempZip.getAbsolutePath());
        zipEntity.setStatus(ZipStatusEnum.INIT);
        zipEntity.setBatchRun(new BatchRunEntity());

        when(batchFileZipRepository.findById(fileZipId)).thenReturn(Optional.of(zipEntity));

        // Mock validator to throw exception for long filename
        String longName = "a".repeat(300) + ".csv";
        doThrow(
                        new InvalidZipEntryNameException(
                                String.format(
                                        "Filename too long: %d characters (max: %d)",
                                        longName.length(), 255)))
                .when(zipSecurityValidator)
                .validateFilename(longName);

        zipProcessingService.extractZip(fileZipId);

        verify(batchFileZipRepository, atLeastOnce())
                .save(
                        argThat(
                                zip ->
                                        zip.getStatus() == ZipStatusEnum.FAILED
                                                && zip.getErrorMessage() != null
                                                && zip.getErrorMessage()
                                                        .contains("Filename too long")));
    }

    @Test
    void shouldHandleTotalSizeExceedsLimit() throws IOException {
        Long fileZipId = 1L;
        lenient()
                .when(zipThresholdProperties.getSize())
                .thenReturn(DataSize.of(100, DataUnit.BYTES));

        File tempZip = createLargeZip();

        BatchFileZipEntity zipEntity = new BatchFileZipEntity();
        zipEntity.setId(fileZipId);
        zipEntity.setPath(tempZip.getAbsolutePath());
        zipEntity.setStatus(ZipStatusEnum.INIT);
        zipEntity.setBatchRun(new BatchRunEntity());

        when(batchFileZipRepository.findById(fileZipId)).thenReturn(Optional.of(zipEntity));

        doThrow(
                        new ZipBombException(
                                "Total uncompressed size exceeds limit: 200 bytes (max: 100 bytes)"))
                .when(zipSecurityValidator)
                .checkZipBomb(any(ZipEntry.class), anyLong(), anyLong());

        zipProcessingService.extractZip(fileZipId);

        verify(batchFileZipRepository, atLeastOnce())
                .save(
                        argThat(
                                zip ->
                                        zip.getStatus() == ZipStatusEnum.FAILED
                                                && zip.getErrorMessage() != null
                                                && zip.getErrorMessage()
                                                        .contains("size exceeds limit")));
    }

    @Test
    void shouldSkipNonCsvFiles() throws IOException {
        Long fileZipId = 1L;
        File tempZip = createZipWithNonCsvFiles();

        BatchFileZipEntity zipEntity = new BatchFileZipEntity();
        zipEntity.setId(fileZipId);
        zipEntity.setPath(tempZip.getAbsolutePath());
        zipEntity.setStatus(ZipStatusEnum.INIT);
        zipEntity.setBatchRun(new BatchRunEntity());

        when(batchFileZipRepository.findById(fileZipId)).thenReturn(Optional.of(zipEntity));
        when(batchFileRepository.findAllByBatchFileZip(zipEntity)).thenReturn(List.of());

        zipProcessingService.extractZip(fileZipId);

        // No batch file records should be saved since no CSV files were present
        verify(batchFileRepository, never()).save(any(BatchFileEntity.class));
    }

    private File createTempZipWithCsv() throws IOException {
        Path secureTempDir = Paths.get(System.getProperty("java.io.tmpdir"), "hmg-ip-map");
        Files.createDirectories(secureTempDir);
        File tempZip = Files.createTempFile(secureTempDir, "test", ".zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempZip))) {
            ZipEntry entry1 = new ZipEntry("country-ipblocks.csv");
            zos.putNextEntry(entry1);
            zos.write("id,name\n1,John".getBytes());
            zos.closeEntry();

            ZipEntry entry2 = new ZipEntry("city-ipblocks.csv");
            zos.putNextEntry(entry2);
            zos.write("id,city\n1,NYC".getBytes());
            zos.closeEntry();
        }
        return tempZip;
    }

    private File createMaliciousZipWithPathTraversal() throws IOException {
        Path secureTempDir = Paths.get(System.getProperty("java.io.tmpdir"), "hmg-ip-map");
        Files.createDirectories(secureTempDir);
        File tempZip = Files.createTempFile(secureTempDir, "malicious", ".zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempZip))) {
            // Attempt path traversal
            ZipEntry entry = new ZipEntry("../../etc/passwd.csv");
            zos.putNextEntry(entry);
            zos.write("malicious content".getBytes());
            zos.closeEntry();
        }
        return tempZip;
    }

    private File createZipWithManyEntries() throws IOException {
        Path secureTempDir = Paths.get(System.getProperty("java.io.tmpdir"), "hmg-ip-map");
        Files.createDirectories(secureTempDir);
        File tempZip = Files.createTempFile(secureTempDir, "many-entries", ".zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempZip))) {
            for (int i = 0; i < 5; i++) {
                ZipEntry entry = new ZipEntry("file" + i + ".csv");
                zos.putNextEntry(entry);
                zos.write("content".getBytes());
                zos.closeEntry();
            }
        }
        return tempZip;
    }

    private File createZipWithNullByteFilename() throws IOException {
        Path secureTempDir = Paths.get(System.getProperty("java.io.tmpdir"), "hmg-ip-map");
        Files.createDirectories(secureTempDir);
        File tempZip = Files.createTempFile(secureTempDir, "null-byte", ".zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempZip))) {
            ZipEntry entry = new ZipEntry("file\0.csv");
            zos.putNextEntry(entry);
            zos.write("content".getBytes());
            zos.closeEntry();
        }
        return tempZip;
    }

    private File createZipWithReservedFilename() throws IOException {
        Path secureTempDir = Paths.get(System.getProperty("java.io.tmpdir"), "hmg-ip-map");
        Files.createDirectories(secureTempDir);
        File tempZip = Files.createTempFile(secureTempDir, "reserved", ".zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempZip))) {
            ZipEntry entry = new ZipEntry("CON.csv");
            zos.putNextEntry(entry);
            zos.write("content".getBytes());
            zos.closeEntry();
        }
        return tempZip;
    }

    private File createZipWithLongFilename() throws IOException {
        Path secureTempDir = Paths.get(System.getProperty("java.io.tmpdir"), "hmg-ip-map");
        Files.createDirectories(secureTempDir);
        File tempZip = Files.createTempFile(secureTempDir, "long-name", ".zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempZip))) {
            // Create a filename longer than 255 characters
            String longName = "a".repeat(300) + ".csv";
            ZipEntry entry = new ZipEntry(longName);
            zos.putNextEntry(entry);
            zos.write("content".getBytes());
            zos.closeEntry();
        }
        return tempZip;
    }

    private File createLargeZip() throws IOException {
        Path secureTempDir = Paths.get(System.getProperty("java.io.tmpdir"), "hmg-ip-map");
        Files.createDirectories(secureTempDir);
        File tempZip = Files.createTempFile(secureTempDir, "large", ".zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempZip))) {
            // Use a valid filename that matches FileTypeEnum pattern
            ZipEntry entry = new ZipEntry("country-ipblocks.csv");
            zos.putNextEntry(entry);
            // Write more than 100 bytes to exceed the mocked limit
            zos.write(new byte[200]);
            zos.closeEntry();
        }
        return tempZip;
    }

    private File createZipWithNonCsvFiles() throws IOException {
        Path secureTempDir = Paths.get(System.getProperty("java.io.tmpdir"), "hmg-ip-map");
        Files.createDirectories(secureTempDir);
        File tempZip = Files.createTempFile(secureTempDir, "non-csv", ".zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempZip))) {
            ZipEntry entry1 = new ZipEntry("readme.txt");
            zos.putNextEntry(entry1);
            zos.write("This is a readme file".getBytes());
            zos.closeEntry();

            ZipEntry entry2 = new ZipEntry("data.json");
            zos.putNextEntry(entry2);
            zos.write("{\"key\": \"value\"}".getBytes());
            zos.closeEntry();
        }
        return tempZip;
    }
}
