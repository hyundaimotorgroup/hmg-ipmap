package com.hmg.ipmap.cache.enums;

import java.util.List;
import lombok.Getter;

@Getter
public enum CacheTable {
    LOCATION("location"),
    LOCATION_NAME("location_name"),
    IP_MAPPING("ip_mapping"),
    IP_ATTRIBUTE("ip_mapping_attribute"),
    IP_SPAN("ip_span");

    private final String tableName;

    CacheTable(String tableName) {
        this.tableName = tableName;
    }

    private static final String USER_ID = "user_id";
    private static final String SCOPE = "scope";

    public List<String> registeredColumns() {
        return switch (this) {
            case LOCATION ->
                    List.of(
                            "id",
                            "attributes",
                            "geoname_id",
                            "location_code",
                            USER_ID,
                            "location_level",
                            SCOPE,
                            "parent_id");
            case LOCATION_NAME -> List.of("locale_code", "location_id", "name");
            case IP_MAPPING ->
                    List.of(
                            "id",
                            "created_at",
                            "ip_notation",
                            "notation_type",
                            "registered_country_geoname_id",
                            "represented_country_geoname_id",
                            SCOPE,
                            "updated_at",
                            "valid_period",
                            "location_id",
                            USER_ID);
            case IP_ATTRIBUTE -> List.of("id", "attributes", "object_name", "ip_mapping_id");
            case IP_SPAN ->
                    List.of(
                            "id",
                            "ip_lower",
                            "ip_upper",
                            "ip_mapping_id",
                            SCOPE,
                            "created_at",
                            USER_ID,
                            "valid_period");
        };
    }

    @Override
    public String toString() {
        return tableName;
    }
}
