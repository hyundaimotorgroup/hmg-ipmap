package com.hmg.ipmap.ingestion.provider;

import com.hmg.ipmap.common.exception.BadRequestException;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.web.multipart.MultipartFile;

/** Marker interface for import file type categories. */
public interface ImportType {

    /**
     * Returns the unique name of this import type, typically the enum constant name (e.g., {@code
     * LOCATION_ZIP}).
     */
    String name();

    /**
     * Returns {@code true} if this import type expects a ZIP file upload.
     *
     * <p>Implementations must explicitly declare their file format rather than relying on naming
     * conventions, so that ZIP magic-byte validation is never silently skipped.
     */
    boolean isZip();

    /**
     * Validates that the uploaded file is acceptable for this import type.
     *
     * <p>For ZIP-based import types ({@link #isZip()} returns {@code true}), the default
     * implementation enforces a {@code .zip} extension and verifies the ZIP magic number ({@code
     * PK\x03\x04}). Non-ZIP import types are skipped by default and should override this method if
     * validation is required.
     *
     * @param file the multipart file to validate
     * @throws BadRequestException if the file does not match the expected format
     */
    default void validate(MultipartFile file) {
        validateIfZipType(file);
    }

    private void validateIfZipType(MultipartFile file) {
        if (!isZip()) {
            return;
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".zip")) {
            throw new BadRequestException("Only ZIP files are accepted");
        }
        try (InputStream is = file.getInputStream()) {
            byte[] header = is.readNBytes(4);
            if (header.length < 4
                    || header[0] != 0x50
                    || header[1] != 0x4B
                    || header[2] != 0x03
                    || header[3] != 0x04) {
                throw new BadRequestException("File is not a valid ZIP archive");
            }
        } catch (IOException _) {
            throw new BadRequestException("Unable to read uploaded file");
        }
    }
}
