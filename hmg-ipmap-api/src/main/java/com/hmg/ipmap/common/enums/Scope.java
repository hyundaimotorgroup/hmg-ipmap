package com.hmg.ipmap.common.enums;

public enum Scope {
    /**
     * @deprecated Retained for legacy sub-client compatibility; scheduled for removal when the
     *     parent/source-IP model is retired.
     */
    @Deprecated(forRemoval = true)
    SUB_CLIENT("SC"),
    /**
     * @deprecated Retained for legacy client compatibility; scheduled for removal when the
     *     parent/source-IP model is retired.
     */
    @Deprecated(forRemoval = true)
    CLIENT("C"),
    GLOBAL("G");

    private final String code;

    Scope(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
