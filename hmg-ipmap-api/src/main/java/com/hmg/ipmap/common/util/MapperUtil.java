package com.hmg.ipmap.common.util;

import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Utility class for JSON mapping operations. Provides centralized ObjectMapper instance and common
 * serialization methods.
 */
@Slf4j
public class MapperUtil {

    private MapperUtil() {
        throw new IllegalStateException("Utility class");
    }

    @Getter private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Converts a map to JSON string with exception handling.
     *
     * @param map the map to convert
     * @param <V> the type of values in the map
     * @return JSON string representation
     */
    public static <V> String toJson(Map<String, V> map) {
        if (map == null) {
            return null;
        }
        return objectMapper.writeValueAsString(map);
    }

    /**
     * Converts a map to JSON string, returning null on failure.
     *
     * @param map the map to convert
     * @param <V> the type of values in the map
     * @return JSON string representation, or null if serialization fails
     */
    public static <V> String toJsonSafe(Map<String, V> map) {
        try {
            return toJson(map);
        } catch (JacksonException e) {
            log.error("Failed to serialize map to JSON", e);
            return null;
        }
    }
}
