package com.hmg.ipmap.cache.enums;

public enum CacheOpsAction {
    LOCATION_UPDATE(CacheTable.LOCATION, 'u'),
    LOCATION_NAME_UPDATE(CacheTable.LOCATION_NAME, 'u'),
    IP_UPDATE(CacheTable.IP_MAPPING, 'u'),
    IP_SPAN_UPDATE(CacheTable.IP_SPAN, 'u'),
    IP_ATTRIBUTE_UPDATE(CacheTable.IP_ATTRIBUTE, 'u'),
    LOCATION_DELETE(CacheTable.LOCATION, 'd'),
    LOCATION_NAME_DELETE(CacheTable.LOCATION_NAME, 'd'),
    IP_DELETE(CacheTable.IP_MAPPING, 'd'),
    IP_SPAN_DELETE(CacheTable.IP_SPAN, 'd'),
    IP_ATTRIBUTE_DELETE(CacheTable.IP_ATTRIBUTE, 'd');

    public final CacheTable cacheTable;
    public final Character operation;

    CacheOpsAction(CacheTable cacheTable, Character operation) {
        this.cacheTable = cacheTable;
        this.operation = operation;
    }

    public boolean isSortedSet() {
        return isIpSpan();
    }

    public boolean isString() {
        return isLocation() || isIpMapping();
    }

    public boolean isLocation() {
        switch (this) {
            case LOCATION_UPDATE, LOCATION_DELETE -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public boolean isIpMapping() {
        switch (this) {
            case IP_UPDATE, IP_DELETE -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public boolean isIpSpan() {
        switch (this) {
            case IP_SPAN_UPDATE, IP_SPAN_DELETE -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public boolean isLocationName() {
        switch (this) {
            case LOCATION_NAME_UPDATE, LOCATION_NAME_DELETE -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
