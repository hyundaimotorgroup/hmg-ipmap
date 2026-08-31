package com.hmg.ipmap.cache.entity;

public enum CacheCollection {
    IP_SPAN("ip_span"),
    IP_MAPPING("ip_mapping"),
    IP_MAPPING_ATTRIBUTE("ip_mapping_attribute"),
    LOCATION("location"),
    LOCATION_NAME("location_name"),
    USER("user");

    private final String tableName;

    CacheCollection(String tableName) {
        this.tableName = tableName;
    }

    public String getTableName() {
        return tableName;
    }
}
