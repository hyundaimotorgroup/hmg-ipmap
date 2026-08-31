package com.hmg.ipmap.ingestion.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hmg.ipmap.common.context.UserContext;
import com.hmg.ipmap.common.context.UserContextHolder;
import com.hmg.ipmap.ingestion.file.AsyncJobRunner;
import com.hmg.ipmap.ingestion.file.entity.BatchRunEntity;
import com.hmg.ipmap.ingestion.file.entity.BatchRunStatusEnum;
import com.hmg.ipmap.ingestion.file.repository.BatchFileDetailRepositoryCustom;
import com.hmg.ipmap.ingestion.file.repository.BatchRunRepository;
import com.hmg.ipmap.ingestion.file.scheduler.BatchJobPollingScheduler;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("BatchJobPollingScheduler Tests")
class BatchJobPollingSchedulerTest {

    @InjectMocks private BatchJobPollingScheduler scheduler;

    @Mock private BatchRunRepository batchRunRepository;

    @Mock private BatchFileDetailRepositoryCustom batchFileDetailRepository;

    @Mock private AsyncJobRunner asyncJobRunner;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "orphanTimeout", Duration.ofSeconds(600));
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    private BatchRunEntity batchRun(String jobId, long batchId, long createdBy) {
        BatchRunEntity run = new BatchRunEntity();
        run.setId(batchId);
        run.setJobId(jobId);
        run.setStatus(BatchRunStatusEnum.IN_PROGRESS);
        run.setCreatedBy(createdBy);
        return run;
    }

    @Nested
    @DisplayName("No active jobs")
    class NoActiveJobsTests {

        @Test
        @DisplayName("Given no IN_PROGRESS jobs, pollAndRecover returns without any action")
        void shouldDoNothingWhenNoActiveJobs() {
            when(batchRunRepository.findByStatus(BatchRunStatusEnum.IN_PROGRESS))
                    .thenReturn(List.of());

            scheduler.pollAndRecover();

            verify(batchFileDetailRepository, never()).resetOrphanedRows(anyLong(), any());
            verify(asyncJobRunner, never()).isRunningLocally(any());
            verify(asyncJobRunner, never()).run(any(), any());
        }
    }

    @Nested
    @DisplayName("Join recovery")
    class JoinRecoveryTests {

        @Test
        @DisplayName("Given job already running locally, run() is skipped")
        void shouldSkipRunWhenJobAlreadyRunningLocally() {
            BatchRunEntity run = batchRun("job-1", 10L, 42L);
            when(batchRunRepository.findByStatus(BatchRunStatusEnum.IN_PROGRESS))
                    .thenReturn(List.of(run));
            when(batchFileDetailRepository.resetOrphanedRows(eq(10L), any(Duration.class)))
                    .thenReturn(0);
            when(asyncJobRunner.isRunningLocally("job-1")).thenReturn(true);

            scheduler.pollAndRecover();

            verify(asyncJobRunner, never()).run(any(), any());
        }

        @Test
        @DisplayName("Given job not running locally, run() is called with correct jobId and userId")
        void shouldJoinJobWhenNotRunningLocally() {
            BatchRunEntity run = batchRun("job-1", 10L, 42L);
            when(batchRunRepository.findByStatus(BatchRunStatusEnum.IN_PROGRESS))
                    .thenReturn(List.of(run));
            when(batchFileDetailRepository.resetOrphanedRows(eq(10L), any(Duration.class)))
                    .thenReturn(0);
            when(asyncJobRunner.isRunningLocally("job-1")).thenReturn(false);

            scheduler.pollAndRecover();

            verify(asyncJobRunner).run("job-1", 42L);
        }

        @Test
        @DisplayName(
                "Given job not running locally, UserContext is set with userId before run() and cleared after")
        void shouldSetAndClearUserContextAroundRun() {
            BatchRunEntity run = batchRun("job-1", 10L, 42L);
            when(batchRunRepository.findByStatus(BatchRunStatusEnum.IN_PROGRESS))
                    .thenReturn(List.of(run));
            when(batchFileDetailRepository.resetOrphanedRows(eq(10L), any(Duration.class)))
                    .thenReturn(0);
            when(asyncJobRunner.isRunningLocally("job-1")).thenReturn(false);

            UserContext[] capturedContext = new UserContext[1];
            doAnswer(
                            invocation -> {
                                capturedContext[0] = UserContextHolder.get();
                                return null;
                            })
                    .when(asyncJobRunner)
                    .run("job-1", 42L);

            scheduler.pollAndRecover();

            assertThat(capturedContext[0]).isNotNull();
            assertThat(capturedContext[0].id()).isEqualTo(42L);
            assertThat(UserContextHolder.get()).isNull();
        }

        @Test
        @DisplayName("Given run() throws, UserContext is still cleared")
        void shouldClearUserContextEvenIfRunThrows() {
            BatchRunEntity run = batchRun("job-1", 10L, 42L);
            when(batchRunRepository.findByStatus(BatchRunStatusEnum.IN_PROGRESS))
                    .thenReturn(List.of(run));
            when(batchFileDetailRepository.resetOrphanedRows(eq(10L), any(Duration.class)))
                    .thenReturn(0);
            when(asyncJobRunner.isRunningLocally("job-1")).thenReturn(false);
            doThrow(new RuntimeException("async failure")).when(asyncJobRunner).run("job-1", 42L);

            assertThrows(RuntimeException.class, () -> scheduler.pollAndRecover());

            assertThat(UserContextHolder.get()).isNull();
        }
    }

    @Nested
    @DisplayName("Orphan recovery")
    class OrphanRecoveryTests {

        @Test
        @DisplayName(
                "Given active job, resetOrphanedRows is called with correct batchId and timeout")
        void shouldCallResetOrphanedRowsWithCorrectArgs() {
            BatchRunEntity run = batchRun("job-1", 10L, 42L);
            when(batchRunRepository.findByStatus(BatchRunStatusEnum.IN_PROGRESS))
                    .thenReturn(List.of(run));
            when(asyncJobRunner.isRunningLocally("job-1")).thenReturn(true);

            scheduler.pollAndRecover();

            ArgumentCaptor<Duration> durationCaptor = ArgumentCaptor.forClass(Duration.class);
            verify(batchFileDetailRepository).resetOrphanedRows(eq(10L), durationCaptor.capture());
            assertThat(durationCaptor.getValue()).isEqualTo(Duration.ofSeconds(600));
        }

        @Test
        @DisplayName("Given multiple active jobs, resetOrphanedRows is called for each batchId")
        void shouldProcessAllActiveJobs() {
            BatchRunEntity run1 = batchRun("job-1", 10L, 1L);
            BatchRunEntity run2 = batchRun("job-2", 20L, 2L);
            when(batchRunRepository.findByStatus(BatchRunStatusEnum.IN_PROGRESS))
                    .thenReturn(List.of(run1, run2));
            when(asyncJobRunner.isRunningLocally(any())).thenReturn(true);

            scheduler.pollAndRecover();

            verify(batchFileDetailRepository).resetOrphanedRows(eq(10L), any(Duration.class));
            verify(batchFileDetailRepository).resetOrphanedRows(eq(20L), any(Duration.class));
        }
    }
}
