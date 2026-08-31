package com.hmg.ipmap.common;

import jakarta.persistence.Table;

public interface CachedEntity {
    default String tableName() {
        Table table = this.getClass().getAnnotation(Table.class);
        if (table != null && !table.name().isEmpty()) {
            return table.name();
        }
        throw new IllegalStateException(
                "Entity "
                        + this.getClass().getSimpleName()
                        + " is missing @Table annotation with name");
    }
}
