package com.hmg.ipmap.ipmapping;

import com.hmg.ipmap.cache.event.CacheEntityListener;
import com.hmg.ipmap.common.CachedEntity;
import com.hmg.ipmap.common.entity.AuditableEntity;
import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.common.util.DateUtil;
import io.hypersistence.utils.hibernate.type.range.PostgreSQLRangeType;
import io.hypersistence.utils.hibernate.type.range.Range;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

/**
 * JPA entity representing a resolved IP address range derived from an {@link IpMappingEntity}.
 *
 * <p>Stores the range as {@code ipLower}/{@code ipUpper} long values and as a PostgreSQL {@code
 * int8range} column ({@code ip_range}), which is kept in sync with the long values by a
 * {@code @PrePersist}/{@code @PreUpdate} callback. A default validity period is applied if none is
 * set.
 *
 * <p>Changes to this entity automatically trigger cache synchronization via {@link
 * CacheEntityListener}.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
@Table(name = "ip_span")
@EntityListeners(CacheEntityListener.class)
public class IpSpanEntity extends AuditableEntity implements CachedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column private Long ipLower;

    @Column private Long ipUpper;

    @ManyToOne
    @NonNull
    @JoinColumn(name = "ip_mapping_id")
    private IpMappingEntity ipMapping;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope")
    private Scope scope;

    @Column(columnDefinition = "TIMESTAMP")
    private Instant createdAt;

    @Column Long userId;

    @Column(columnDefinition = "TIMESTAMP")
    private Instant validPeriod;

    @Type(PostgreSQLRangeType.class)
    @Column(name = "ip_range", columnDefinition = "int8range")
    private Range<Long> ipRange;

    @PrePersist
    @PreUpdate
    void syncIpRangeAndValidPeriod() {
        if (ipLower != null && ipUpper != null) {
            this.ipRange = Range.closed(ipLower, ipUpper);
        }
        if (validPeriod == null) {
            this.validPeriod = DateUtil.FAR_FUTURE_VALID_PERIOD;
        }
    }
}
