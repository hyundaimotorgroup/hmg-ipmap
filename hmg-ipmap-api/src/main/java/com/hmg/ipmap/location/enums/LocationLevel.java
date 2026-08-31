package com.hmg.ipmap.location.enums;

/**
 * Marker interface for location hierarchy levels.
 *
 * <p>Each data provider defines its own implementing enum:
 *
 * <ul>
 *   <li>{@link DefaultLocationLevel} — CONTINENT, COUNTRY, REGION, CITY
 * </ul>
 *
 * <p>Code that must handle any provider (e.g. {@link com.hmg.ipmap.location.LocationServiceImpl})
 * declares parameters as {@code LocationLevel}. Provider-specific code uses the concrete enum.
 */
public interface LocationLevel {

    /** Returns the enum constant name, e.g. {@code "CONTINENT"}, {@code "REGION"}. */
    String name();

    /** Ordinal position within the hierarchy (lower = closer to the root). */
    int getOrder();

    /**
     * Returns the string prefix used as the location-cache key segment, e.g. {@code "CONTINENT-"}.
     */
    String getPrefixId();
}
