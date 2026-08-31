package com.hmg.ipmap.ingestion.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hmg.ipmap.common.exception.NotFoundException;
import com.hmg.ipmap.ingestion.file.dto.JobStatusResponseDto;
import com.hmg.ipmap.ingestion.file.projection.BatchStatusProjection;
import com.hmg.ipmap.ingestion.file.repository.BatchRunRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileImportService Tests")
class FileImportServiceTest {

    @InjectMocks private FileImportServiceImpl fileImportService;

    @Mock private BatchRunRepository batchRunRepository;

    @Nested
    @DisplayName("Get Job Status")
    class JobStatusTests {

        @Test
        @DisplayName("Given job exists when getJobStatus is called then return job details")
        void shouldReturnJobStatus() {
            String jobId = "job123";
            BatchStatusProjection projection = mock(BatchStatusProjection.class);
            when(projection.getBatchStatus()).thenReturn("COMPLETED");
            when(projection.getStatedAt()).thenReturn(LocalDateTime.of(2025, 11, 21, 10, 0));
            when(projection.getFinishedAt()).thenReturn(LocalDateTime.of(2025, 11, 21, 11, 0));
            when(projection.getTotalFileZip()).thenReturn(5);
            when(projection.getTotalPercentage()).thenReturn(100);

            when(batchRunRepository.getStatusByJobId(jobId)).thenReturn(Optional.of(projection));

            JobStatusResponseDto response = fileImportService.getJobStatus(jobId);

            assertNotNull(response);
            assertEquals(jobId, response.getJobId());
            assertEquals("COMPLETED", response.getStatus());
            assertEquals(5, response.getImportTypeCount());
            assertEquals(100, response.getTotalPercentage());
            assertEquals("2025-11-21T10:00", response.getStartedAt());
            assertEquals("2025-11-21T11:00", response.getFinishedAt());
        }

        @Test
        @DisplayName(
                "Given job does not exist when getJobStatus is called then throw NotFoundException")
        void shouldThrowNotFoundException() {
            String jobId = "job999";
            when(batchRunRepository.getStatusByJobId(jobId)).thenReturn(Optional.empty());

            NotFoundException exception =
                    assertThrows(
                            NotFoundException.class, () -> fileImportService.getJobStatus(jobId));
            assertEquals("Job Status Not Found", exception.getMessage());
        }

        @Test
        @DisplayName(
                "Given job exists with null dates when getJobStatus is called then return empty date strings")
        void shouldHandleNullDates() {
            String jobId = "job456";
            BatchStatusProjection projection = mock(BatchStatusProjection.class);
            when(projection.getBatchStatus()).thenReturn("IN_PROGRESS");
            when(projection.getTotalFileZip()).thenReturn(3);
            when(projection.getTotalPercentage()).thenReturn(50);
            when(projection.getStatedAt()).thenReturn(null);
            when(projection.getFinishedAt()).thenReturn(null);

            when(batchRunRepository.getStatusByJobId(jobId)).thenReturn(Optional.of(projection));

            JobStatusResponseDto response = fileImportService.getJobStatus(jobId);

            assertEquals("", response.getStartedAt());
            assertEquals("", response.getFinishedAt());
        }
    }
}
