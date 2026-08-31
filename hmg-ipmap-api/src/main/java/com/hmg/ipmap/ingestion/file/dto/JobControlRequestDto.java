package com.hmg.ipmap.ingestion.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JobControlRequestDto {
    @Schema(description = "The action to control the job. e.g.: run, cancel", example = "run")
    @NotBlank
    private String action;
}
