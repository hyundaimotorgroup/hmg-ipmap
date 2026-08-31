package com.hmg.ipmap.user.dto;

import com.hmg.ipmap.common.enums.UserType;
import com.hmg.ipmap.user.UserEntity;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public class UserSearchSpecification {

    public static final String PARENT = "parent";

    private UserSearchSpecification() {}

    public static Specification<UserEntity> byFilters(
            Long id,
            Collection<UserType> types,
            Collection<Long> parentIds,
            boolean includeNullParent) {

        return userTypeIn(types)
                .and(idEquals(id))
                .or(parentInIncludingNull(parentIds, includeNullParent));
    }

    // --- IN + include null ---
    public static Specification<UserEntity> parentInIncludingNull(
            Collection<Long> parentIds, boolean includeNull) {
        return (root, query, cb) -> {
            List<Predicate> orPredicates = new ArrayList<>();

            if (parentIds != null && !parentIds.isEmpty()) {
                Path<Long> parentIdPath = root.join(PARENT, JoinType.LEFT).get("id");
                orPredicates.add(parentIdPath.in(parentIds));
            }

            if (includeNull) {
                orPredicates.add(cb.isNull(root.get(PARENT)));
            }

            if (orPredicates.isEmpty()) {
                return null;
            }

            return cb.or(orPredicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<UserEntity> userTypeIn(Collection<UserType> types) {
        return (root, query, cb) -> {
            if (types == null || types.isEmpty()) return null;
            return root.get("userType").in(types);
        };
    }

    public static Specification<UserEntity> idEquals(Long id) {
        return (root, query, cb) -> id == null ? null : cb.equal(root.get("id"), id);
    }
}
