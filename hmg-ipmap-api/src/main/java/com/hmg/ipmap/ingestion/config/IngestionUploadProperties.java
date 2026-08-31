package com.hmg.ipmap.ingestion.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "app.ingestion.upload")
@Validated
@Getter
@Setter
public class IngestionUploadProperties {

    /** Directory where uploaded ZIP files are stored. */
    @NotBlank(message = "app.ingestion.upload.folder must not be blank")
    private String folder;

    /** Batch insert size when storing CSV lines. Default: 10000 */
    private int insertBatchSize = 10_000;
}
