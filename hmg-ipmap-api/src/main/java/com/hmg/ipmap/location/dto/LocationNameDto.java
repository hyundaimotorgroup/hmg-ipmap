package com.hmg.ipmap.location.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for deserialising a locale-to-name map from arbitrary JSON properties.
 *
 * <p>Uses {@link com.fasterxml.jackson.annotation.JsonAnySetter} to collect all unknown JSON
 * properties into the {@link #names} map, allowing clients to supply names as top-level fields
 * keyed by locale code.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationNameDto {

    private Map<String, String> names = new HashMap<>();

    /**
     * Jackson {@code @JsonAnySetter} handler that accumulates unknown JSON properties into the
     * names map, keyed by locale code.
     *
     * @param key the locale code (e.g. {@code "en"}, {@code "de"})
     * @param value the display name for that locale
     */
    @JsonAnySetter
    public void addName(String key, String value) {
        names.put(key, value);
    }
}
