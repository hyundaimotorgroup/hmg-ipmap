package com.hmg.ipmap.ingestion.file.zip;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

@Component
@ConfigurationProperties(prefix = "app.ingestion.zip.threshold")
@Getter
@Setter
public class ZipThresholdProperties {
    /** Maximum number of entries allowed in ZIP file. Default: 20 */
    private int entries = 20;

    /** Maximum total uncompressed size of ZIP archive. Default: 1GB */
    private DataSize size = DataSize.ofGigabytes(1);

    /** Maximum size per individual entry. Default: 300MB */
    private DataSize entrySize = DataSize.ofMegabytes(300);

    /** Maximum compression ratio (uncompressed/compressed). Default: 10 */
    private long ratio = 10L;
}
