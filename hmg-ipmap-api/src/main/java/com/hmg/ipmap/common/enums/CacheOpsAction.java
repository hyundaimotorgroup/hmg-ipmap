package com.hmg.ipmap.common.enums;

public enum CacheOpsAction {
    LOCATION_UPDATE("location"),
    LOCATION_NAME_UPDATE("location_name"),
    IP_UPDATE("ip_mapping"),
    IP_SPAN_UPDATE("ip_span"),
    IP_ATTRIBUTE_UPDATE("ip_mapping_attribute"),
    LOCATION_DELETE("location"),
    IP_DELETE("ip_mapping"),
    IP_SPAN_DELETE("ip_span");

    public final String tableName;

    CacheOpsAction(String tableName) {
        this.tableName = tableName;
    }

    public boolean isSortedSet() {
        switch (this) {
            case IP_SPAN_UPDATE, IP_SPAN_DELETE -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
