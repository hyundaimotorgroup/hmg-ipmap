package com.hmg.ipmap.common.enums;

import com.hmg.ipmap.common.constant.ApiPaths;
import java.util.Set;
import org.springframework.util.AntPathMatcher;

public enum UserType {
    ADMIN(
            Set.of(
                    ApiPaths.USERS,
                    ApiPaths.LOCATIONS,
                    ApiPaths.IP_LOCATION,
                    ApiPaths.IP_MAPPINGS,
                    ApiPaths.ADMIN_OPS,
                    ApiPaths.BATCH_FILE_IMPORTS,
                    ApiPaths.CACHE_OPS)),
    CLIENT(Set.of(ApiPaths.USERS, ApiPaths.IP_LOCATION, ApiPaths.IP_MAPPINGS, ApiPaths.LOCATIONS)),
    SUB_CLIENT(
            Set.of(ApiPaths.USERS, ApiPaths.IP_LOCATION, ApiPaths.IP_MAPPINGS, ApiPaths.LOCATIONS));

    private final Set<String> allowedEndpoints;
    private static final AntPathMatcher matcher = new AntPathMatcher();

    UserType(Set<String> allowedEndpoints) {
        this.allowedEndpoints = allowedEndpoints;
    }

    public boolean canAccess(String endpoint) {
        return allowedEndpoints.stream().anyMatch(pattern -> matcher.match(pattern, endpoint));
    }
}
