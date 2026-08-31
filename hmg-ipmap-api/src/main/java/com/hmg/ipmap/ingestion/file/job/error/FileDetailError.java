package com.hmg.ipmap.ingestion.file.job.error;

import java.io.Serializable;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class FileDetailError implements Serializable {
    private Long fileDetailId;
    private String message;
}
