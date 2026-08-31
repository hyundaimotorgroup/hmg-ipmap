package com.hmg.ipmap.ingestion.file.enums;

import com.hmg.ipmap.ingestion.provider.ImportType;
import java.util.Optional;

/** Generic provider-independent import file type categories. */
public enum DefaultImportType implements ImportType {
    LOCATION_ZIP("location-zip"),
    IP_BLOCK_ZIP("ip-block-zip");

    private final String importTypeKey;

    DefaultImportType(String importTypeKey) {
        this.importTypeKey = importTypeKey;
    }

    public static Optional<ImportType> fromString(String key) {
        for (DefaultImportType type : DefaultImportType.values()) {
            if (type.importTypeKey.equals(key) || type.name().equals(key)) return Optional.of(type);
        }
        return Optional.empty();
    }

    @Override
    public boolean isZip() {
        return true;
    }
}
