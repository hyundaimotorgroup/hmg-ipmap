package com.hmg.ipmap.ingestion.file.job;

import com.hmg.ipmap.ingestion.file.job.model.BaseLocation;
import com.hmg.ipmap.location.LocationEntity;
import com.hmg.ipmap.location.LocationNameEntity;
import com.hmg.ipmap.location.LocationNameId;
import com.hmg.ipmap.user.UserEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
public class LocationProcessingContext<T extends BaseLocation> {
    private final UserEntity userEntity;
    private final String localeCode;
    private final List<T> locations;

    private final Map<String, LocationEntity> locationCache = new ConcurrentHashMap<>();
    private final Map<LocationNameId, LocationNameEntity> locationNameCache =
            new ConcurrentHashMap<>();

    public LocationProcessingContext(List<T> locations, UserEntity userEntity) {
        this.locations = new ArrayList<>(locations);
        this.userEntity = userEntity;
        this.localeCode =
                locations.stream()
                        .map(BaseLocation::getLocaleCode)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse("en");
    }

    public Set<String> getIsoCodesFromLocation() {
        return locations.stream()
                .flatMap(locationItem -> locationItem.extractIsoCodes().stream())
                .collect(Collectors.toSet());
    }

    public Set<Long> getGeonameIdsFromLocation() {
        return locations.stream()
                .map(BaseLocation::extractGeonameId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
