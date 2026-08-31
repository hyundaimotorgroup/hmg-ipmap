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
public class CountryTerritory extends AbstractTerritory {
    private String countryIsoCode;
    private String countryName;
    private Long geonameId;

    @Override
    public String getIdentity() {
        return LocationIdentity.of(DefaultLocationLevel.COUNTRY, countryIsoCode);
    }
}
