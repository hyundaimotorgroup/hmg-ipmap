package com.hmg.ipmap.cache.helper;

import com.hmg.ipmap.cache.enums.CacheOpsAction;
import java.util.List;

public class CacheOperationMapper {

    private CacheOperationMapper() {
        throw new IllegalStateException("helper class");
    }

    private static final String TABLE_LOCATION = "LOCATION";
    private static final String TABLE_LOCATION_NAME = "LOCATION_NAME";
    private static final String TABLE_IP_MAPPING = "IP_MAPPING";
    private static final String TABLE_IP_SPAN = "IP_SPAN";
    private static final String TABLE_IP_MAPPING_ATTRIBUTE = "IP_MAPPING_ATTRIBUTE";

    private static final String ACTION_UPDATE = "UPDATE";
    private static final String ACTION_DELETE = "DELETE";

    private static final List<String> TABLE_KEY =
            List.of(
                    TABLE_LOCATION,
                    TABLE_LOCATION_NAME,
                    TABLE_IP_MAPPING,
                    TABLE_IP_MAPPING_ATTRIBUTE,
                    TABLE_IP_SPAN);

    public static CacheOpsAction map(String action, String tableName) {
        if (action == null || tableName == null) {
            throw new IllegalArgumentException("action and tableName must not be null");
        }

        String act = action.trim().toUpperCase();
        String table = tableName.trim().toUpperCase();

        if (!TABLE_KEY.contains(table)) {
            throw new IllegalArgumentException("Unknown table name: " + tableName);
        }

        return switch (act) {
            case ACTION_UPDATE ->
                    switch (table) {
                        case TABLE_LOCATION -> CacheOpsAction.LOCATION_UPDATE;
                        case TABLE_LOCATION_NAME -> CacheOpsAction.LOCATION_NAME_UPDATE;
                        case TABLE_IP_SPAN -> CacheOpsAction.IP_SPAN_UPDATE;
                        case TABLE_IP_MAPPING_ATTRIBUTE -> CacheOpsAction.IP_ATTRIBUTE_UPDATE;
                        case TABLE_IP_MAPPING -> CacheOpsAction.IP_UPDATE;
                        default ->
                                throw new IllegalArgumentException(
                                        "Unhandled UPDATE for: " + tableName);
                    };

            case ACTION_DELETE ->
                    switch (table) {
                        case TABLE_LOCATION -> CacheOpsAction.LOCATION_DELETE;
                        case TABLE_LOCATION_NAME -> CacheOpsAction.LOCATION_NAME_DELETE;
                        case TABLE_IP_SPAN -> CacheOpsAction.IP_SPAN_DELETE;
                        case TABLE_IP_MAPPING_ATTRIBUTE -> CacheOpsAction.IP_ATTRIBUTE_DELETE;
                        case TABLE_IP_MAPPING -> CacheOpsAction.IP_DELETE;
                        default ->
                                throw new IllegalArgumentException(
                                        "Unhandled DELETE for: " + tableName);
                    };

            default -> throw new IllegalArgumentException("Unknown action: " + action);
        };
    }
}
