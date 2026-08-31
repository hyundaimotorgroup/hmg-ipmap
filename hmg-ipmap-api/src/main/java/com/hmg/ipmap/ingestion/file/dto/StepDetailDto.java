package com.hmg.ipmap.ingestion.file.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StepDetailDto {

    private String status;

    private String startedAt;

    private String finishedAt;

    private String exitMessage;
}
