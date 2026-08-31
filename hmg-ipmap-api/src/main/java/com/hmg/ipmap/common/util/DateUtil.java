package com.hmg.ipmap.common.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class DateUtil {

    private static final String TIMESTAMP_PATTERN = "yyyy-MM-dd HH:mm:ss.SSSZ";

    public static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern(TIMESTAMP_PATTERN);

    public static final Instant FAR_FUTURE_VALID_PERIOD =
            LocalDateTime.of(9999, 12, 31, 0, 0, 0).toInstant(ZoneOffset.UTC);

    private DateUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static boolean isBackDate(String dateStr) {
        try {
            LocalDate inputDate = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            LocalDate today = LocalDate.now();
            return inputDate.isBefore(today);
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse date {}", e.getMessage());
            return true;
        }
    }

    public static Instant stringISOToInstant(String dateTime) {
        LocalDate localDate = LocalDate.parse(dateTime);
        return localDate.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
