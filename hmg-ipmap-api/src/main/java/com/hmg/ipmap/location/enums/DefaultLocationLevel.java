package com.hmg.ipmap.location.enums;

public enum DefaultLocationLevel implements LocationLevel {
    CONTINENT(1),
    COUNTRY(2),
    REGION(3),
    CITY(4);

    private final int order;

    DefaultLocationLevel(final int order) {
        this.order = order;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public String getPrefixId() {
        return name() + "-";
    }
}
