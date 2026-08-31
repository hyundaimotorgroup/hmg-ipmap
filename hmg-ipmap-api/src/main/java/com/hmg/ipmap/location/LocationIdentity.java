package com.hmg.ipmap.location;

import com.hmg.ipmap.location.enums.LocationLevel;

/**
 * Single source of truth for computing location cache/lookup keys.
 *
 * <p>All code that needs to turn a location into a string key (batch item {@code getIdentity()}
 * methods, entity-side cache preloading, name registration) must go through this class. This
 * prevents the format from drifting if {@link LocationLevel#getPrefixId()} is ever changed.
 *
 * <p>Key format: {@code "{LEVEL_PREFIX}{identifier}"}, where:
 *
 * <ul>
 *   <li>For CONTINENT, COUNTRY, SUBDIVISION1, SUBDIVISION2 — identifier is the ISO / location code
 *   <li>For CITY — identifier is the geonameId string representation
 * </ul>
 */
public final class LocationIdentity {

    private LocationIdentity() {}

    public static String of(LocationLevel level, String identifier) {
        return level.getPrefixId() + identifier;
    }

    /**
     * Computes the key from an existing {@link LocationEntity}.
     *
     * <p>Returns {@code null} when the entity is {@code null}.
     */
    public static String of(LocationEntity entity) {
        if (entity == null) {
            return null;
        }
        String level = entity.getLocationLevel();
        String identifier =
                "CITY".equals(level)
                        ? String.valueOf(entity.getGeonameId())
                        : entity.getLocationCode();
        return String.format("%s-%s", level, identifier);
    }
}
