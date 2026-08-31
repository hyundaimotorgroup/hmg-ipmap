package com.hmg.ipmap.common.util;

import java.util.UUID;

public final class UuidUtil {

    private UuidUtil() {}

    public static String generateUuid() {
        return UUID.randomUUID().toString();
    }
}
