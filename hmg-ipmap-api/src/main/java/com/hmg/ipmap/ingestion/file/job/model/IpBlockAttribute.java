package com.hmg.ipmap.ingestion.file.job.model;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IpBlockAttribute {
    private Long fileDetailId;
    private String network;
    private String postalCode;
    private String latitude;
    private String longitude;
    private String timezone;

    public Map<String, Map<String, Object>> toMap() {
        Map<String, Object> map = new HashMap<>();
        putIfNotBlank(map, "latitude", latitude);
        putIfNotBlank(map, "longitude", longitude);
        putIfNotBlank(map, "timezone", timezone);
        putIfNotBlank(map, "postal_code", postalCode);
        return Map.of("LOCATION", map);
    }

    protected static void putIfNotBlank(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }
}
