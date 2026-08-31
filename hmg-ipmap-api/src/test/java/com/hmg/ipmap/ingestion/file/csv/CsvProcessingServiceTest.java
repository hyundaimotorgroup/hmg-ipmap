package com.hmg.ipmap.ingestion.file.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hmg.ipmap.common.context.UserContext;
import com.hmg.ipmap.common.context.UserContextHolder;
import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.common.enums.UserType;
import com.hmg.ipmap.common.exception.InternalServerErrorException;
import com.hmg.ipmap.ingestion.config.IngestionUploadProperties;
import com.hmg.ipmap.ingestion.file.entity.BatchFileEntity;
import com.hmg.ipmap.ingestion.file.entity.BatchFileStatusEnum;
import com.hmg.ipmap.ingestion.file.repository.BatchFileDetailRepository;
import com.hmg.ipmap.ingestion.file.repository.BatchFileRepository;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CsvProcessingServiceTest {
    @InjectMocks private CsvProcessingServiceImpl csvProcessingService;
    @Mock private BatchFileRepository batchFileRepository;
    @Mock private BatchFileDetailRepository batchFileDetailRepository;
    @Mock private IngestionUploadProperties uploadProperties;

    @BeforeEach
    void setUp() {
        Mockito.lenient().when(uploadProperties.getInsertBatchSize()).thenReturn(10_000);
    }

    @Test
    void shouldProcessCsvAndSaveDetails() throws Exception {
        File tempCsv = createTempCsvFile();
        BatchFileEntity batchFile = Mockito.mock(BatchFileEntity.class);
        batchFile.setId(1L);
        batchFile.setFileName("test.csv");
        batchFile.setPath(tempCsv.getAbsolutePath());

        UserContext userContext =
                new UserContext(1L, "admin", UserType.ADMIN, "1.2.3.4", Scope.GLOBAL, null, null);
        UserContextHolder.set(userContext);

        when(batchFile.getPath()).thenReturn(tempCsv.getAbsolutePath());

        csvProcessingService.readCsvAndStoreLines(batchFile);

        verify(batchFileDetailRepository, atLeastOnce()).saveAllInBatch(Mockito.anyList());
        verify(batchFile, times(1)).setLineCount(anyInt());

        verify(batchFileRepository).save(batchFile);
    }

    @Test
    void shouldHandleErrorWhenFileNotFound() {
        BatchFileEntity batchFile = new BatchFileEntity();
        batchFile.setId(1L);
        batchFile.setFileName("missing.csv");
        batchFile.setPath("invalid/path.csv");

        assertThrows(
                InternalServerErrorException.class,
                () -> csvProcessingService.readCsvAndStoreLines(batchFile));

        assertEquals(BatchFileStatusEnum.FAILED, batchFile.getStatus());
        assertNotNull(batchFile.getErrorMessage());
    }

    private File createTempCsvFile() throws Exception {
        Path secureTempDir = Paths.get(System.getProperty("java.io.tmpdir"), "hmg-ip-map");
        Files.createDirectories(secureTempDir);
        Path tempFile = Files.createTempFile(secureTempDir, "test-", ".csv");
        try (FileWriter writer = new FileWriter(tempFile.toFile())) {
            writer.write("network,represent_country\n");
            writer.write("1,Indonesia\n");
            writer.write("2,Korea\n");
        }
        return tempFile.toFile();
    }
}
