package com.hmg.ipmap.ipmapping;

import com.hmg.ipmap.cache.event.CacheEntityListener;
import com.hmg.ipmap.common.CachedEntity;
import com.hmg.ipmap.common.entity.AuditableEntity;
import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.common.util.DateUtil;
import com.hmg.ipmap.ipnotation.NotationType;
import com.hmg.ipmap.location.IpMappingAttributeEntity;
import com.hmg.ipmap.location.LocationEntity;
import com.hmg.ipmap.user.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * JPA entity representing an IP mapping that associates an IP notation with a geographic location.
 *
 * <p>Supports single addresses, CIDR blocks, ranges, wildcards, and comma-separated arrays. Each
 * mapping is scoped to a user and has an optional validity period; if none is supplied, it defaults
 * to {@link com.hmg.ipmap.common.util.DateUtil#FAR_FUTURE_VALID_PERIOD}.
 *
 * <p>Changes to this entity automatically trigger cache synchronisation via {@link
 * CacheEntityListener}.
 */
@Entity
@Getter
@Setter
@Table(name = "ip_mapping")
@EntityListeners(CacheEntityListener.class)
public class IpMappingEntity extends AuditableEntity implements CachedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ip_mapping_seq")
    @SequenceGenerator(name = "ip_mapping_seq", sequenceName = "ip_mapping_seq")
    private Long id;

    @Column private String ipNotation;

    @Enumerated(EnumType.STRING)
    @Column
    private NotationType notationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope")
    private Scope scope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private LocationEntity location;

    @Column(name = "registered_country_geoname_id")
    private Long registeredCountryGeonameId;

    @Column(name = "represented_country_geoname_id")
    private Long representedCountryGeonameId;

    @Column(columnDefinition = "TIMESTAMP")
    private Instant validPeriod;

    @Column(columnDefinition = "TIMESTAMP")
    private Instant updatedAt;

    @OneToMany(mappedBy = "ipMapping", fetch = FetchType.LAZY)
    private List<IpMappingAttributeEntity> attributes;

    @PrePersist
    @PreUpdate
    void setValidPeriodIfAbsent() {
        if (this.validPeriod == null) {
            this.validPeriod = DateUtil.FAR_FUTURE_VALID_PERIOD;
        }
    }
}
