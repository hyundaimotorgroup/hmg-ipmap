package com.hmg.ipmap.ingestion.file.enums;

import java.util.Optional;
import lombok.Getter;

@Getter
public enum JobActionEnum {
    RUN("run"),
    CANCEL("cancel");

    private final String value;

    JobActionEnum(String value) {
        this.value = value;
    }

    public static Optional<JobActionEnum> fromValue(String value) {
        for (JobActionEnum e : JobActionEnum.values()) {
            if (e.value.equals(value.toLowerCase())) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }
}
