package com.hmg.ipmap.ingestion.file.job.reader;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

@Slf4j
public abstract class AbstractCsvReader<T> {

    protected abstract T mapRow(String[] row, Long fileDetailId);

    protected abstract boolean validate(String[] row);

    private final ThreadLocal<CsvParser> csvParserHolder;

    protected AbstractCsvReader() {
        CsvParserSettings settings = new CsvParserSettings();
        settings.getFormat().setDelimiter(',');
        settings.getFormat().setQuote('"');
        settings.setNullValue("");
        csvParserHolder = ThreadLocal.withInitial(() -> new CsvParser(settings));
    }

    private String[] parse(String line) {
        return csvParserHolder.get().parseLine(line);
    }

    /**
     * Parses a raw CSV line and maps it to a record of type {@code T}.
     *
     * <p>The line is split using the configured CSV parser (comma-separated, double-quote escaped)
     * and the resulting fields are passed to {@link #mapRow(String[], Long)}.
     *
     * @param line a single raw CSV line; must not be null
     * @return the mapped record, or {@code null} if the parser returns no fields
     */
    public T readLine(String line, Long fileDetailId) {
        String[] row = parse(line);
        if (row == null) {
            log.warn("CSV parser returned no fields for line {}, skipping", fileDetailId);
            return null;
        }
        return mapRow(row, fileDetailId);
    }

    /**
     * Parses a raw CSV line and checks whether it represents a valid record.
     *
     * <p>The line is split using the configured CSV parser and the resulting fields are passed to
     * {@link #validate(String[])}. Lines that fail validation should be skipped rather than mapped.
     *
     * @param line a single raw CSV line; must not be null
     * @return {@code true} if the line is valid and safe to map, {@code false} otherwise
     */
    public boolean validateLine(String line) {
        String[] row = parse(line);
        return row != null && validate(row);
    }

    /**
     * Parses a string value into a {@link Long}, trimming whitespace first.
     *
     * <p>Returns {@code null} if the input is blank or cannot be parsed as a number. This is used
     * for optional numeric fields in CSV rows (e.g., geoname IDs) where an empty or missing value
     * is valid and should be represented as {@code null} rather than causing a parse failure.
     *
     * @param s the raw string value from the CSV field; may be null or blank
     * @return the parsed {@link Long} value, or {@code null} if the input is blank or non-numeric
     */
    protected Long parseNullableLong(String s) {
        String trimmed = StringUtils.trimToNull(s);
        if (trimmed == null) return null;
        if (!NumberUtils.isParsable(trimmed)) return null;
        return Long.parseLong(trimmed);
    }

    protected boolean toBooleanFrom01(String s) {
        return "1".equals(s == null ? null : s.trim());
    }

    /**
     * Releases the thread-local {@link CsvParser} held for the current thread. Call this after the
     * step that uses this reader completes to prevent ThreadLocal retention in pooled threads.
     */
    public void cleanup() {
        csvParserHolder.remove();
    }
}
