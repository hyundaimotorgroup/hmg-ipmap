package com.hmg.ipmap.location;

import com.hmg.ipmap.cache.event.CacheEntityListener;
import com.hmg.ipmap.common.CachedEntity;
import com.hmg.ipmap.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity storing a single localised name for a {@link LocationEntity}.
 *
 * <p>The composite primary key is {@link LocationNameId}, combining the location ID and a BCP 47
 * locale code. One row per locale per location is maintained.
 *
 * <p>Changes to this entity automatically trigger cache synchronisation via {@link
 * CacheEntityListener}.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "location_name")
@EntityListeners(CacheEntityListener.class)
public class LocationNameEntity extends AuditableEntity implements CachedEntity {

    public LocationNameEntity(Long locationId, String localeCode, String name) {
        this.locationNameId = new LocationNameId(locationId, localeCode);
        this.name = name;
    }

    /** Composite primary key composed of location ID and locale code. */
    @EmbeddedId private LocationNameId locationNameId;

    /** Location that this name row describes. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false, insertable = false, updatable = false)
    private LocationEntity location;

    /**
     * Display name of the location in the locale identified by {@link
     * LocationNameId#getLocaleCode()}.
     */
    @Column(nullable = false)
    private String name;
}
