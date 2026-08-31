package com.hmg.ipmap.cache.helper;

import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CsvCodec {

    private CsvCodec() {
        throw new IllegalStateException("helper class");
    }

    // ==================== ENCODE FROM MAP (Map → CSV) ====================

    public static <T> String encodeFromMapToCsv(Map<String, String> data, Class<T> cacheDtoClass) {
        if (data == null || data.isEmpty()) return "";

        String[] row = buildRowFromAnnotation(data, cacheDtoClass);

        StringWriter stringWriter = new StringWriter();

        CsvWriterSettings settings = new CsvWriterSettings();
        settings.getFormat().setDelimiter(',');
        settings.getFormat().setLineSeparator("\n");

        CsvWriter csvWriter = new CsvWriter(stringWriter, settings);
        csvWriter.writeRow(row);
        csvWriter.close();

        return stringWriter.toString().trim();
    }

    // ==================== PARSE TOKENS (CSV → String[]) ====================

    /**
     * Splits one CSV line into tokens, correctly handling quoted fields (e.g. JSON with commas).
     * Follows RFC 4180: fields may be enclosed in double-quotes; a literal quote inside a quoted
     * field is represented as two consecutive double-quotes (""). Returns an empty array for
     * null/blank input.
     */
    public static String[] parseTokens(String csv) {
        if (csv == null || csv.isBlank()) return new String[0];

        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        int i = 0;

        while (i < csv.length()) {
            char c = csv.charAt(i);
            if (isOpeningQuote(c, inQuotes, current)) {
                inQuotes = true;
                i++;
            } else if (c == '"' && inQuotes) {
                if (isEscapedQuote(csv, i)) {
                    current.append('"');
                    i += 2; // skip both quotes of the escaped pair
                } else {
                    inQuotes = false; // closing quote
                    i++;
                }
            } else if (isFieldDelimiter(c, inQuotes)) {
                tokens.add(current.toString());
                current.setLength(0);
                i++;
            } else {
                current.append(c);
                i++;
            }
        }
        tokens.add(current.toString());

        return tokens.toArray(new String[0]);
    }

    // RFC 4180: a quoted field must start with " as its very first character.
    // " appearing mid-field (e.g. inside raw JSON like {"k":v}) is a literal char.
    private static boolean isOpeningQuote(char c, boolean inQuotes, StringBuilder current) {
        return c == '"' && !inQuotes && current.isEmpty();
    }

    // Two consecutive quotes inside a quoted field represent a single literal quote ("").
    private static boolean isEscapedQuote(String csv, int i) {
        return i + 1 < csv.length() && csv.charAt(i + 1) == '"';
    }

    private static boolean isFieldDelimiter(char c, boolean inQuotes) {
        return c == ',' && !inQuotes;
    }

    // ==================== PRIVATE HELPERS ====================

    private static <T> String[] buildRowFromAnnotation(
            Map<String, String> data, Class<T> cacheDtoClass) {
        // Scan semua field yang punya @Parsed, sort by index
        TreeMap<Integer, String> indexToKey = new TreeMap<>();

        for (Field field : cacheDtoClass.getDeclaredFields()) {
            Parsed parsed = field.getAnnotation(Parsed.class);
            if (parsed == null) continue;

            int index = parsed.index();
            String snakeKey = toSnakeCase(field.getName()); // ipLower → ip_lower
            indexToKey.put(index, snakeKey);
        }

        String[] row = new String[indexToKey.size()];

        indexToKey.forEach(
                (index, key) -> {
                    if (index < row.length) {
                        row[index] = data.getOrDefault(key, "");
                    }
                });

        return row;
    }

    private static String toSnakeCase(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
