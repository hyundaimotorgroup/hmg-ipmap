package com.hmg.ipmap.ingestion.file.zip;

import com.hmg.ipmap.ingestion.config.IngestionUploadProperties;
import com.hmg.ipmap.ingestion.file.csv.CsvProcessingService;
import com.hmg.ipmap.ingestion.file.entity.BatchFileZipEntity;
import com.hmg.ipmap.ingestion.file.repository.BatchFileRepository;
import com.hmg.ipmap.ingestion.file.repository.BatchFileZipRepository;
import com.hmg.ipmap.ingestion.file.repository.BatchRunRepository;
import com.hmg.ipmap.ingestion.file.zip.validator.ZipSecurityValidator;
import com.hmg.ipmap.ingestion.provider.DataProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Default (provider-agnostic) ZIP processing service with no post-extraction logic. */
@Service
@ConditionalOnProperty(name = "app.data-provider", havingValue = "default", matchIfMissing = true)
public class DefaultZipProcessingServiceImpl extends ZipProcessingServiceImpl {

    public DefaultZipProcessingServiceImpl(
            BatchFileRepository batchFileRepository,
            BatchFileZipRepository batchFileZipRepository,
            CsvProcessingService csvProcessingService,
            BatchRunRepository batchRunRepository,
            ZipThresholdProperties zipThresholdProperties,
            ZipSecurityValidator securityValidator,
            DataProvider dataProvider,
            IngestionUploadProperties uploadProperties) {
        super(
                uploadProperties,
                batchFileRepository,
                batchFileZipRepository,
                csvProcessingService,
                batchRunRepository,
                zipThresholdProperties,
                securityValidator,
                dataProvider);
    }

    @Override
    protected void postProcess(BatchFileZipEntity zip) {
        // No post-processing needed for the default provider
    }
}
