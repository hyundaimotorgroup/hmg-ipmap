package com.hmg.ipmap.ingestion.file.zip.validator;

import com.hmg.ipmap.common.exception.AlreadyExistException;
import com.hmg.ipmap.ingestion.file.zip.ZipThresholdProperties;
import com.hmg.ipmap.ingestion.file.zip.exception.InvalidZipEntryNameException;
import com.hmg.ipmap.ingestion.file.zip.exception.ZipBombException;
import com.hmg.ipmap.ingestion.file.zip.exception.ZipEntrySizeLimitExceededException;
import com.hmg.ipmap.ingestion.file.zip.exception.ZipSlipException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Validates ZIP file security to prevent Zip Slip, Zip Bomb, and other security vulnerabilities.
 * Implements OWASP security recommendations for ZIP file handling.
 *
 * <p>Security measures:
 *
 * <ul>
 *   <li>Path traversal (Zip Slip) prevention
 *   <li>Zip Bomb detection via compression ratio and size limits
 *   <li>Filename sanitization (null bytes, control characters, reserved names)
 *   <li>Symbolic link escape prevention
 *   <li>File overwrite protection
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZipSecurityValidator {

    private static final int MAX_FILENAME_LENGTH = 255;
    private static final String[] WINDOWS_RESERVED_NAMES = {
        "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7",
        "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    };

    private final ZipThresholdProperties zipThresholdProperties;

    /**
     * Validates ZIP entry filename for security issues.
     *
     * @param entryName the ZIP entry name to validate
     * @throws InvalidZipEntryNameException if filename is invalid or dangerous
     */
    public void validateFilename(String entryName) {
        if (StringUtils.isEmpty(entryName)) {
            throw new InvalidZipEntryNameException("Entry name is null or empty");
        }

        if (entryName.length() > MAX_FILENAME_LENGTH) {
            throw new InvalidZipEntryNameException(
                    String.format(
                            "Filename too long: %d characters (max: %d)",
                            entryName.length(), MAX_FILENAME_LENGTH));
        }

        // Check for null bytes and control characters
        if (entryName.contains("\0")) {
            throw new InvalidZipEntryNameException("Filename contains null byte characters");
        }

        if (entryName.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidZipEntryNameException("Filename contains control characters");
        }

        // Check for Windows reserved names
        String baseNameUpper = extractBaseName(entryName).toUpperCase();
        for (String reserved : WINDOWS_RESERVED_NAMES) {
            if (baseNameUpper.equals(reserved)) {
                throw new InvalidZipEntryNameException("Filename uses reserved name: " + reserved);
            }
        }
    }

    /**
     * Validates path to prevent Zip Slip (path traversal) attacks.
     *
     * @param entryName the ZIP entry name
     * @param targetDir the target extraction directory
     * @return validated and normalized path
     * @throws ZipSlipException if path traversal is detected
     */
    public Path validateZipSlip(String entryName, Path targetDir) {
        Path resolvedPath = targetDir.resolve(entryName).normalize();
        if (!resolvedPath.startsWith(targetDir)) {
            throw new ZipSlipException("Invalid entry: " + resolvedPath.getFileName());
        }
        return resolvedPath;
    }

    /**
     * Validates that no symbolic links in the path escape the target directory.
     *
     * @param path the path to validate
     * @param targetDir the target directory boundary
     * @throws ZipSlipException if symlink points outside target directory
     * @throws IOException if I/O error occurs during validation
     */
    public void validateNoSymlinkEscape(Path path, Path targetDir) throws IOException {
        Path current = path;
        while (current != null && current.startsWith(targetDir)) {
            if (Files.exists(current) && Files.isSymbolicLink(current)) {
                Path realPath = current.toRealPath();
                if (!realPath.startsWith(targetDir)) {
                    throw new ZipSlipException(
                            "Symlink points outside target directory: " + current);
                }
            }
            current = current.getParent();
        }
    }

    /**
     * Validates file does not already exist to prevent overwrite attacks.
     *
     * @param filePath the file path to check
     * @throws AlreadyExistException if file already exists
     */
    public void validateFileNotExists(Path filePath) {
        if (Files.exists(filePath)) {
            throw new AlreadyExistException("File already exists: " + filePath.getFileName());
        }
    }

    /**
     * Checks for Zip Bomb attacks by monitoring compression ratio, per-entry size, and cumulative
     * archive size.
     *
     * @param entry the ZIP entry being processed
     * @param totalSizeEntry cumulative uncompressed bytes written for the current entry
     * @param totalSizeArchive cumulative uncompressed bytes written across all entries so far
     * @throws ZipBombException if a Zip Bomb is detected
     */
    public void checkZipBomb(ZipEntry entry, long totalSizeEntry, long totalSizeArchive) {
        long compressedSize = entry.getCompressedSize();

        // Check compression ratio only if compressed size is known AND positive
        if (compressedSize > 0) {
            double compressionRatio = (double) totalSizeEntry / compressedSize;
            if (compressionRatio > zipThresholdProperties.getRatio()) {
                throw new ZipBombException(
                        String.format(
                                "Suspicious compression ratio detected: %.2f (max: %d)",
                                compressionRatio, zipThresholdProperties.getRatio()));
            }
        } else {
            // If compressed size is unknown or invalid, enforce per-entry size limit
            if (totalSizeEntry > zipThresholdProperties.getEntrySize().toBytes()) {
                throw new ZipBombException(
                        String.format(
                                "Entry size exceeds limit: %d bytes (max: %d bytes per entry when compressed size unknown)",
                                totalSizeEntry, zipThresholdProperties.getEntrySize().toBytes()));
            }
        }

        // Guard against ZIP bombs that spread payload across many small entries
        if (totalSizeArchive > zipThresholdProperties.getSize().toBytes()) {
            throw new ZipBombException(
                    String.format(
                            "Total uncompressed size exceeds limit: %d bytes (max: %d bytes)",
                            totalSizeArchive, zipThresholdProperties.getSize().toBytes()));
        }
    }

    /**
     * Validates per-entry size limit.
     *
     * @param totalSizeEntry cumulative size of current entry
     * @throws ZipEntrySizeLimitExceededException if entry exceeds size limit
     */
    public void validateEntrySizeLimit(long totalSizeEntry) {
        if (totalSizeEntry > zipThresholdProperties.getEntrySize().toBytes()) {
            throw new ZipEntrySizeLimitExceededException(
                    String.format(
                            "Entry size exceeds limit: %d bytes (max: %d bytes)",
                            totalSizeEntry, zipThresholdProperties.getEntrySize().toBytes()));
        }
    }

    private String extractBaseName(String fileName) {
        String name = fileName;
        int lastSlash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            name = fileName.substring(lastSlash + 1);
        }

        int dotIndex = name.lastIndexOf('.');
        return dotIndex > 0 ? name.substring(0, dotIndex) : name;
    }
}
