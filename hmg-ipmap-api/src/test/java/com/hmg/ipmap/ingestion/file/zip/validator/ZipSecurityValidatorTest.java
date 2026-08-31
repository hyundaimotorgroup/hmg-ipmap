package com.hmg.ipmap.ingestion.file.zip.validator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.hmg.ipmap.common.exception.GlobalException;
import com.hmg.ipmap.ingestion.file.zip.ZipThresholdProperties;
import com.hmg.ipmap.ingestion.file.zip.exception.ZipBombException;
import com.hmg.ipmap.ingestion.file.zip.exception.ZipSlipException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;

@ExtendWith(MockitoExtension.class)
@DisplayName("ZipSecurityValidator Tests")
class ZipSecurityValidatorTest {

    @InjectMocks private ZipSecurityValidator zipSecurityValidator;

    @Mock private ZipThresholdProperties zipThresholdProperties;

    private Path testDir;

    @BeforeEach
    void setUp() throws IOException {
        testDir = Paths.get(System.getProperty("java.io.tmpdir"), "test-validator");
        Files.createDirectories(testDir);

        // Configure default thresholds
        lenient().when(zipThresholdProperties.getEntries()).thenReturn(100);
        lenient()
                .when(zipThresholdProperties.getSize())
                .thenReturn(DataSize.of(1, DataUnit.GIGABYTES));
        lenient()
                .when(zipThresholdProperties.getEntrySize())
                .thenReturn(DataSize.of(300, DataUnit.MEGABYTES));
        lenient().when(zipThresholdProperties.getRatio()).thenReturn(10L);
    }

    @AfterEach
    void tearDown() throws IOException {
        deleteDirectoryRecursively(testDir);
    }

    private void deleteDirectoryRecursively(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(
                                path -> {
                                    try {
                                        Files.delete(path);
                                    } catch (IOException _) {
                                        // Ignore cleanup errors in tests
                                    }
                                });
            }
        }
    }

    public static Stream<Arguments> invalidFilenames() {
        return Stream.of(
                Arguments.of(null, "null or empty"),
                Arguments.of("", "null or empty"),
                Arguments.of("file\0.csv", "null byte"),
                Arguments.of("file\u0001.csv", "control characters"));
    }

    @Nested
    @DisplayName("validateFilename Tests")
    class ValidateFilenameTests {

        @Test
        @DisplayName("Should accept valid filenames")
        void shouldAcceptValidFilename() {
            assertDoesNotThrow(() -> zipSecurityValidator.validateFilename("valid-file.csv"));
            assertDoesNotThrow(() -> zipSecurityValidator.validateFilename("country-ipblocks.csv"));
            assertDoesNotThrow(
                    () -> zipSecurityValidator.validateFilename("file_with_underscores.csv"));
        }

        @ParameterizedTest
        @MethodSource(
                "com.hmg.ipmap.ingestion.file.zip.validator.ZipSecurityValidatorTest#invalidFilenames")
        @DisplayName("Should reject invalid filenames")
        void shouldRejectInvalidFilename(String filename, String expectedErrorMessage) {
            GlobalException exception =
                    assertThrows(
                            GlobalException.class,
                            () -> zipSecurityValidator.validateFilename(filename));
            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
            assertTrue(exception.getMessage().contains(expectedErrorMessage));
        }

        @Test
        @DisplayName("Should reject filename too long (>255 characters)")
        void shouldRejectFilenameTooLong() {
            String longFilename = "a".repeat(256) + ".csv";
            GlobalException exception =
                    assertThrows(
                            GlobalException.class,
                            () -> zipSecurityValidator.validateFilename(longFilename));
            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
            assertTrue(exception.getMessage().contains("Filename too long"));
        }

        @Test
        @DisplayName("Should accept filename at 255 characters limit")
        void shouldAcceptFilenameAt255Characters() {
            String maxLengthFilename = "a".repeat(251) + ".csv"; // 251 + 4 = 255
            assertDoesNotThrow(() -> zipSecurityValidator.validateFilename(maxLengthFilename));
        }

        @Test
        @DisplayName("Should reject Windows reserved names")
        void shouldRejectWindowsReservedNames() {
            String[] reservedNames = {
                "CON.csv", "PRN.csv", "AUX.csv", "NUL.csv", "COM1.csv", "LPT1.csv"
            };

            for (String reservedName : reservedNames) {
                GlobalException exception =
                        assertThrows(
                                GlobalException.class,
                                () -> zipSecurityValidator.validateFilename(reservedName));
                assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
                assertTrue(exception.getMessage().contains("reserved name"));
            }
        }

        @Test
        @DisplayName("Should reject Windows reserved names (case insensitive)")
        void shouldRejectWindowsReservedNamesIgnoreCase() {
            GlobalException exception =
                    assertThrows(
                            GlobalException.class,
                            () -> zipSecurityValidator.validateFilename("con.csv"));
            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
            assertTrue(exception.getMessage().contains("reserved name"));

            exception =
                    assertThrows(
                            GlobalException.class,
                            () -> zipSecurityValidator.validateFilename("CoN.csv"));
            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
            assertTrue(exception.getMessage().contains("reserved name"));
        }

        @Test
        @DisplayName("Should accept filename containing reserved name as substring")
        void shouldAcceptFilenameContainingReservedNameAsSubstring() {
            // "ACON" contains "CON" but is not a reserved name itself
            assertDoesNotThrow(() -> zipSecurityValidator.validateFilename("ACON.csv"));
            assertDoesNotThrow(() -> zipSecurityValidator.validateFilename("CONFIG.csv"));
        }
    }

    @Nested
    @DisplayName("validateZipSlip Tests")
    class ValidateZipSlipTests {

        @Test
        @DisplayName("Should accept valid path")
        void shouldAcceptValidPath() {
            Path result = zipSecurityValidator.validateZipSlip("file.csv", testDir);
            assertNotNull(result);
            assertTrue(result.startsWith(testDir));
        }

        @Test
        @DisplayName("Should accept path in subdirectory")
        void shouldAcceptPathInSubdirectory() {
            Path result = zipSecurityValidator.validateZipSlip("subdir/file.csv", testDir);
            assertNotNull(result);
            assertTrue(result.startsWith(testDir));
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "../../etc/passwd", // Path traversal with double dots
                    "/etc/passwd", // Absolute path
                    "subdir/../../etc/passwd" // Path traversal in middle
                })
        @DisplayName("Should reject path traversal attempts")
        void shouldRejectPathTraversal(String maliciousPath) {
            ZipSlipException exception =
                    assertThrows(
                            ZipSlipException.class,
                            () -> zipSecurityValidator.validateZipSlip(maliciousPath, testDir));
            assertTrue(exception.getMessage().contains("Invalid entry"));
        }

        @Test
        @DisplayName("Should normalize valid path")
        void shouldNormalizeValidPath() {
            Path result = zipSecurityValidator.validateZipSlip("subdir/../file.csv", testDir);
            assertNotNull(result);
            assertTrue(result.startsWith(testDir));
            assertEquals(testDir.resolve("file.csv").normalize(), result);
        }
    }

    @Nested
    @DisplayName("validateNoSymlinkEscape Tests")
    class ValidateNoSymlinkEscapeTests {

        @Test
        @DisplayName("Should accept path without symlinks")
        void shouldAcceptPathWithoutSymlinks() {
            Path validPath = testDir.resolve("file.csv");
            assertDoesNotThrow(
                    () -> zipSecurityValidator.validateNoSymlinkEscape(validPath, testDir));
        }

        @Test
        @DisplayName("Should accept non-existent path")
        void shouldAcceptNonExistentPath() {
            Path nonExistentPath = testDir.resolve("nonexistent/file.csv");
            assertDoesNotThrow(
                    () -> zipSecurityValidator.validateNoSymlinkEscape(nonExistentPath, testDir));
        }

        @Test
        @DisplayName("Should accept symlink pointing inside target directory")
        void shouldAcceptSymlinkPointingInsideTargetDir() throws IOException {
            // Create a file inside target dir
            Path targetFile = testDir.resolve("target.csv");
            Files.createFile(targetFile);

            // Create symlink pointing to it
            Path symlink = testDir.resolve("symlink.csv");
            try {
                Files.createSymbolicLink(symlink, targetFile);

                assertDoesNotThrow(
                        () -> zipSecurityValidator.validateNoSymlinkEscape(symlink, testDir));
            } catch (UnsupportedOperationException | IOException e) {
                // Skip test on systems that don't support symlinks or have permission issues
                System.out.println(
                        "Skipping symlink test - not supported on this system: " + e.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("validateFileNotExists Tests")
    class ValidateFileNotExistsTests {

        @Test
        @DisplayName("Should accept non-existent file")
        void shouldAcceptNonExistentFile() {
            Path nonExistentFile = testDir.resolve("nonexistent.csv");
            assertDoesNotThrow(() -> zipSecurityValidator.validateFileNotExists(nonExistentFile));
        }

        @Test
        @DisplayName("Should reject existing file")
        void shouldRejectExistingFile() throws IOException {
            Path existingFile = testDir.resolve("existing.csv");
            Files.createFile(existingFile);

            GlobalException exception =
                    assertThrows(
                            GlobalException.class,
                            () -> zipSecurityValidator.validateFileNotExists(existingFile));
            assertEquals(HttpStatus.CONFLICT, exception.getStatus());
            assertTrue(exception.getMessage().contains("already exists"));
        }
    }

    @Nested
    @DisplayName("checkZipBomb Tests")
    class CheckZipBombTests {

        @Test
        @DisplayName("Should accept normal compression ratio")
        void shouldAcceptNormalCompressionRatio() {
            ZipEntry entry = new ZipEntry("file.csv");
            entry.setCompressedSize(1000);

            long totalSizeEntry = 5000; // Compression ratio = 5:1

            assertDoesNotThrow(() -> zipSecurityValidator.checkZipBomb(entry, totalSizeEntry, 0L));
        }

        @Test
        @DisplayName("Should reject high compression ratio (zip bomb)")
        void shouldRejectHighCompressionRatio() {
            when(zipThresholdProperties.getRatio()).thenReturn(10L);

            ZipEntry entry = new ZipEntry("file.csv");
            entry.setCompressedSize(1000);

            long totalSizeEntry = 15000; // Compression ratio = 15:1 (exceeds limit of 10)

            ZipBombException exception =
                    assertThrows(
                            ZipBombException.class,
                            () -> zipSecurityValidator.checkZipBomb(entry, totalSizeEntry, 0L));
            assertTrue(exception.getMessage().contains("compression ratio"));
        }

        @Test
        @DisplayName("Should accept compression ratio at limit")
        void shouldAcceptCompressionRatioAtLimit() {
            when(zipThresholdProperties.getRatio()).thenReturn(10L);

            ZipEntry entry = new ZipEntry("file.csv");
            entry.setCompressedSize(1000);

            long totalSizeEntry = 10000; // Compression ratio = 10:1 (exactly at limit)

            assertDoesNotThrow(() -> zipSecurityValidator.checkZipBomb(entry, totalSizeEntry, 0L));
        }

        @Test
        @DisplayName("Should enforce entry size limit when compressed size unknown")
        void shouldEnforceEntrySizeLimitWhenCompressedSizeUnknown() {
            when(zipThresholdProperties.getEntrySize())
                    .thenReturn(DataSize.of(100, DataUnit.MEGABYTES));

            ZipEntry entry = new ZipEntry("file.csv");
            entry.setCompressedSize(-1); // Unknown compressed size

            long totalSizeEntry = 150 * 1024 * 1024; // 150 MB (exceeds 100 MB limit)

            ZipBombException exception =
                    assertThrows(
                            ZipBombException.class,
                            () -> zipSecurityValidator.checkZipBomb(entry, totalSizeEntry, 0L));
            assertTrue(exception.getMessage().contains("Entry size exceeds limit"));
        }

        @Test
        @DisplayName("Should accept entry size when compressed size unknown")
        void shouldAcceptEntrySizeWhenCompressedSizeUnknown() {
            when(zipThresholdProperties.getEntrySize())
                    .thenReturn(DataSize.of(100, DataUnit.MEGABYTES));

            ZipEntry entry = new ZipEntry("file.csv");
            entry.setCompressedSize(-1); // Unknown compressed size

            long totalSizeEntry = 50 * 1024 * 1024; // 50 MB (within 100 MB limit)

            assertDoesNotThrow(() -> zipSecurityValidator.checkZipBomb(entry, totalSizeEntry, 0L));
        }

        @Test
        @DisplayName("Should handle zero compressed size")
        void shouldHandleZeroCompressedSize() {
            ZipEntry entry = new ZipEntry("file.csv");
            entry.setCompressedSize(0);

            long totalSizeEntry = 1000;

            // Should not check compression ratio when compressed size is 0
            assertDoesNotThrow(() -> zipSecurityValidator.checkZipBomb(entry, totalSizeEntry, 0L));
        }

        @Test
        @DisplayName("Should not throw when total archive size is within limit")
        void shouldNotThrowWhenTotalArchiveSizeWithinLimit() {
            when(zipThresholdProperties.getSize()).thenReturn(DataSize.of(1, DataUnit.GIGABYTES));

            ZipEntry entry = new ZipEntry("file.csv");
            entry.setCompressedSize(1000);

            long totalSizeArchive = 500 * 1024 * 1024L; // 500 MB (within 1 GB limit)

            assertDoesNotThrow(
                    () -> zipSecurityValidator.checkZipBomb(entry, 1000L, totalSizeArchive));
        }

        @Test
        @DisplayName("Should not throw when total archive size is exactly at limit")
        void shouldNotThrowWhenTotalArchiveSizeAtLimit() {
            when(zipThresholdProperties.getSize()).thenReturn(DataSize.of(100, DataUnit.MEGABYTES));

            ZipEntry entry = new ZipEntry("file.csv");
            entry.setCompressedSize(1000);

            long totalSizeArchive = 100 * 1024 * 1024L; // Exactly 100 MB

            assertDoesNotThrow(
                    () -> zipSecurityValidator.checkZipBomb(entry, 1000L, totalSizeArchive));
        }

        @Test
        @DisplayName("Should throw ZipBombException when total archive size exceeds limit")
        void shouldThrowZipBombWhenTotalArchiveSizeExceedsLimit() {
            when(zipThresholdProperties.getSize()).thenReturn(DataSize.of(100, DataUnit.MEGABYTES));

            ZipEntry entry = new ZipEntry("file.csv");
            entry.setCompressedSize(1000);

            long totalSizeArchive = 150 * 1024 * 1024L; // 150 MB (exceeds 100 MB limit)

            assertThrows(
                    ZipBombException.class,
                    () -> zipSecurityValidator.checkZipBomb(entry, 1000L, totalSizeArchive));
        }

        @Test
        @DisplayName(
                "Should include size information in exception message when archive limit exceeded")
        void shouldIncludeSizeInfoInExceptionMessageForArchive() {
            when(zipThresholdProperties.getSize()).thenReturn(DataSize.of(100, DataUnit.MEGABYTES));

            ZipEntry entry = new ZipEntry("file.csv");
            entry.setCompressedSize(1000);

            long totalSizeArchive = 200 * 1024 * 1024L;

            ZipBombException exception =
                    assertThrows(
                            ZipBombException.class,
                            () ->
                                    zipSecurityValidator.checkZipBomb(
                                            entry, 1000L, totalSizeArchive));
            assertTrue(exception.getMessage().contains("Total uncompressed size exceeds limit"));
        }

        @Test
        @DisplayName("Should throw when cumulative size across entries exceeds limit")
        void shouldThrowWhenCumulativeSizeAcrossEntriesExceedsLimit() {
            when(zipThresholdProperties.getSize()).thenReturn(DataSize.of(100, DataUnit.MEGABYTES));

            ZipEntry entry = new ZipEntry("file.csv");
            entry.setCompressedSize(1000);

            // Simulate accumulation: first entry 60 MB, second entry brings total to 120 MB
            long afterFirstEntry = 60 * 1024 * 1024L;
            long afterSecondEntry = 120 * 1024 * 1024L;

            assertDoesNotThrow(
                    () -> zipSecurityValidator.checkZipBomb(entry, 1000L, afterFirstEntry));
            assertThrows(
                    ZipBombException.class,
                    () -> zipSecurityValidator.checkZipBomb(entry, 1000L, afterSecondEntry));
        }
    }

    @Nested
    @DisplayName("validateEntrySizeLimit Tests")
    class ValidateEntrySizeLimitTests {

        @Test
        @DisplayName("Should accept entry size within limit")
        void shouldAcceptEntrySizeWithinLimit() {
            when(zipThresholdProperties.getEntrySize())
                    .thenReturn(DataSize.of(300, DataUnit.MEGABYTES));

            long totalSizeEntry = 100 * 1024 * 1024; // 100 MB

            assertDoesNotThrow(() -> zipSecurityValidator.validateEntrySizeLimit(totalSizeEntry));
        }

        @Test
        @DisplayName("Should accept entry size at limit")
        void shouldAcceptEntrySizeAtLimit() {
            when(zipThresholdProperties.getEntrySize())
                    .thenReturn(DataSize.of(300, DataUnit.MEGABYTES));

            long totalSizeEntry = 300 * 1024 * 1024L; // Exactly 300 MB

            assertDoesNotThrow(() -> zipSecurityValidator.validateEntrySizeLimit(totalSizeEntry));
        }

        @Test
        @DisplayName("Should reject entry size exceeding limit")
        void shouldRejectEntrySizeExceedingLimit() {
            when(zipThresholdProperties.getEntrySize())
                    .thenReturn(DataSize.of(300, DataUnit.MEGABYTES));

            long totalSizeEntry = 400 * 1024 * 1024L; // 400 MB (exceeds 300 MB limit)

            GlobalException exception =
                    assertThrows(
                            GlobalException.class,
                            () -> zipSecurityValidator.validateEntrySizeLimit(totalSizeEntry));
            assertEquals(HttpStatusCode.valueOf(413), exception.getStatus());
            assertTrue(exception.getMessage().contains("Entry size exceeds limit"));
        }
    }
}
