package com.hmg.ipmap.location.dto;

import com.hmg.ipmap.location.LocationEntity;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Factory for {@link org.springframework.data.jpa.domain.Specification} predicates used when
 * filtering {@link com.hmg.ipmap.location.LocationEntity} records.
 *
 * <p>All methods are static; the class is not instantiable.
 */
public class LocationSearchSpecification {

    public static final String PARENT = "parent";

    private LocationSearchSpecification() {}

    /**
     * Builds a composite {@link Specification} from the given filter flags; currently delegates to
     * the parent-null filter.
     *
     * @param includeNullParent {@code true} to restrict results to root-level locations
     * @return the composed {@link Specification}
     */
    public static Specification<LocationEntity> byFilters(boolean includeNullParent) {

        return parentIdIsNull(includeNullParent);
    }

    /**
     * Returns a {@link Specification} that optionally restricts results to locations without a
     * parent (i.e. root-level locations).
     *
     * @param isNull {@code true} to add a predicate requiring the parent to be null
     * @return a {@link Specification} with the parent-null predicate, or one that matches all
     *     records if {@code isNull} is {@code false}
     */
    public static Specification<LocationEntity> parentIdIsNull(boolean isNull) {
        return (root, query, cb) -> {
            List<Predicate> orPredicates = new ArrayList<>();

            if (isNull) {
                orPredicates.add(cb.isNull(root.get(PARENT)));
            }

            if (orPredicates.isEmpty()) {
                return null;
            }

            return cb.or(orPredicates.toArray(new Predicate[0]));
        };
    }
}
