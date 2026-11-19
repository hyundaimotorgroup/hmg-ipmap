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
    CLIENT(Set.of(ApiPaths.IP_LOCATION)),
    /**
     * @deprecated Retained for existing sub-client authentication and reads; scheduled for removal
     *     when the legacy parent/source-IP model is retired. New sub-clients cannot be created.
     */
    @Deprecated(forRemoval = true)
    SUB_CLIENT(Set.of(ApiPaths.IP_LOCATION));

    private final Set<String> allowedEndpoints;
    private static final AntPathMatcher matcher = new AntPathMatcher();

    UserType(Set<String> allowedEndpoints) {
        this.allowedEndpoints = allowedEndpoints;
    }

    public boolean canAccess(String endpoint) {
        return allowedEndpoints.stream().anyMatch(pattern -> matcher.match(pattern, endpoint));
    }
}
