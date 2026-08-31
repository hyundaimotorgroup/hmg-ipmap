package com.hmg.ipmap.ingestion.file.job.model;

import com.hmg.ipmap.location.LocationIdentity;
import com.hmg.ipmap.location.enums.DefaultLocationLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ContinentTerritory extends AbstractTerritory {
    private String continentCode;
    private String continentName;

    @Override
    public String getIdentity() {
        return LocationIdentity.of(DefaultLocationLevel.CONTINENT, continentCode);
    }
}
