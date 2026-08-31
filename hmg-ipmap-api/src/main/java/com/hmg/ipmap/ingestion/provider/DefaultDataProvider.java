package com.hmg.ipmap.ingestion.provider;

import com.hmg.ipmap.common.exception.BadRequestException;
import com.hmg.ipmap.ingestion.file.enums.DefaultFileType;
import com.hmg.ipmap.ingestion.file.enums.DefaultImportType;
import com.hmg.ipmap.ingestion.file.enums.FileType;
import java.util.Locale;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.data-provider", havingValue = "default", matchIfMissing = true)
public class DefaultDataProvider implements DataProvider {

    @Override
    public ImportType resolveImportType(String importTypeKey) {
        return DefaultImportType.fromString(importTypeKey)
                .orElseThrow(
                        () -> new BadRequestException("Invalid import type: " + importTypeKey));
    }

    @Override
    public Optional<FileType> detectFileType(ImportType importType, String entryName) {
        if (!(importType instanceof DefaultImportType defaultImportType)) {
            return Optional.empty();
        }

        String lower = entryName.toLowerCase(Locale.ROOT);

        if (lower.contains("block") || lower.contains("ip")) {
            return resolveIpBlockType(defaultImportType);
        }

        if (lower.contains("location")) {
            return resolveLocationType(defaultImportType);
        }

        return Optional.empty();
    }

    private Optional<FileType> resolveIpBlockType(DefaultImportType defaultImportType) {
        if (DefaultImportType.IP_BLOCK_ZIP.equals(defaultImportType))
            return Optional.of(DefaultFileType.IP_BLOCK);

        return Optional.empty();
    }

    private Optional<FileType> resolveLocationType(DefaultImportType defaultImportType) {
        if (DefaultImportType.LOCATION_ZIP.equals(defaultImportType))
            return Optional.of(DefaultFileType.LOCATION);

        return Optional.empty();
    }
}
