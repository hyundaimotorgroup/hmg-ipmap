package com.hmg.ipmap.ingestion.provider;

import com.hmg.ipmap.ingestion.file.enums.FileType;
import java.util.Optional;

public interface DataProvider {

    /**
     * Resolves a URL path segment (e.g., "location-zip") to an ImportType. Throws {@link
     * com.hmg.ipmap.common.exception.BadRequestException} if the key is not supported by this
     * provider.
     */
    ImportType resolveImportType(String importTypeKey);

    /**
     * Detects the BatchFileType for a ZIP entry filename given its parent ImportType. Return empty
     * to skip the entry.
     */
    Optional<FileType> detectFileType(ImportType importType, String entryName);
}
