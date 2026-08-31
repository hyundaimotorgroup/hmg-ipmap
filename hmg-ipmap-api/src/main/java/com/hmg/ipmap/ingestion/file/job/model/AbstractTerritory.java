package com.hmg.ipmap.ingestion.file.job.model;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class AbstractTerritory {
    private String metroCode;
    private String timeZone;
    private boolean isInEuropeanUnion;
    private Map<String, Object> attributes;

    public abstract String getIdentity();
}
