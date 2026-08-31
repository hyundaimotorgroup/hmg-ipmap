package com.hmg.ipmap.ingestion.file.dto;

import com.hmg.ipmap.ingestion.provider.ImportType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
public record UploadResponseDto(
        @Schema(description = "The job id", example = "2025-11-05") String jobId,
        @Schema(description = "The import type", example = "city-zip") ImportType importType,
        @Schema(description = "The uploaded file name", example = "location.zip")
                String fileName) {}
