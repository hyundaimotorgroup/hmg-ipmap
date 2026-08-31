package com.hmg.ipmap.ingestion.file.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class JobStatusResponseDto {
    @Schema(description = "The job id", example = "2025-11-05")
    private String jobId;

    @Schema(description = "The processing progress in percent", example = "70")
    private Integer totalPercentage;

    @Schema(description = "Total uploaded file", example = "6")
    private Integer importTypeCount;

    @Schema(
            description =
                    "The job status. e.g.: RECEIVED, UPLOADING, READY, IN_PROGRESS, COMPLETED, FAILED, CANCELED",
            example = "COMPLETED")
    private String status;

    @Schema(description = "When job is started", example = "2025-11-21T10:00")
    private String startedAt;

    @Schema(description = "When job is finished", example = "2025-11-22T10:00")
    private String finishedAt;
}
