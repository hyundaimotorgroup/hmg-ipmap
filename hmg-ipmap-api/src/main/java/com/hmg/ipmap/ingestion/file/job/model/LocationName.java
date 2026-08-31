package com.hmg.ipmap.ingestion.file.job.model;

import com.hmg.ipmap.location.LocationIdentity;
import com.hmg.ipmap.location.enums.LocationLevel;
import java.util.Objects;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LocationName {
    private Long fileDetailId;
    private Long geonameId;
    private String locationCode;
    private String localeCode;
    private String name;
    private LocationLevel locationLevel;

    public String getIdentity() {
        String identifier =
                Objects.equals(locationLevel.name(), "CITY")
                        ? String.valueOf(geonameId)
                        : locationCode;
        return LocationIdentity.of(locationLevel, identifier);
    }
}
