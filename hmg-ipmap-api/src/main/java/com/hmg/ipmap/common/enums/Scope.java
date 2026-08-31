package com.hmg.ipmap.common.enums;

public enum Scope {
    SUB_CLIENT("SC"),
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
