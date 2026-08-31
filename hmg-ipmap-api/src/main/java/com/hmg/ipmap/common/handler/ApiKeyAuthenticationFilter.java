package com.hmg.ipmap.common.handler;

import com.hmg.ipmap.common.context.UserContext;
import com.hmg.ipmap.common.context.UserContextHolder;
import com.hmg.ipmap.common.enums.Scope;
import com.hmg.ipmap.common.enums.UserType;
import com.hmg.ipmap.common.exception.GlobalException;
import com.hmg.ipmap.user.UserEntity;
import com.hmg.ipmap.user.UserService;
import com.hmg.ipmap.user.exception.AccessDeniedException;
import com.hmg.ipmap.user.exception.ApiKeyUnauthorizeException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final HandlerExceptionResolver handlerExceptionResolver;
    private final UserService userService;

    private static final String API_KEY_HEADER = "X-HMGIPMAP-APIKEY";
    private static final String SOURCE_IP_HEADER = "X-HMGIPMAP-SOURCEIP";

    private static final String API_KEY_NOT_AUTHORIZE = "API key is required and cannot be empty";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return isPublicEndpoint(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        try {
            authenticateAndProcessRequest(request, response, filterChain);
        } catch (GlobalException ex) {
            handlerExceptionResolver.resolveException(request, response, null, ex);
        } finally {
            UserContextHolder.clear();
        }
    }

    private void authenticateAndProcessRequest(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String apiKey = request.getHeader(API_KEY_HEADER);
        if (StringUtils.isBlank(apiKey)) {
            throw new ApiKeyUnauthorizeException(API_KEY_NOT_AUTHORIZE);
        }

        String sourceIp = request.getHeader(SOURCE_IP_HEADER);
        UserEntity userEntity = getUserEntity(apiKey, sourceIp);

        if (userEntity == null) {
            throw new ApiKeyUnauthorizeException(API_KEY_NOT_AUTHORIZE);
        }

        String requestURI = request.getRequestURI();
        if (!userEntity.getUserType().canAccess(requestURI)) {
            throw new AccessDeniedException();
        }

        setUserContext(userEntity);

        String remoteIp = request.getRemoteAddr();

        logUserAccess(userEntity, requestURI, remoteIp, sourceIp);

        filterChain.doFilter(request, response);
    }

    private UserEntity getUserEntity(String apiKey, String sourceIp) {
        if (StringUtils.isNotBlank(sourceIp)) {
            return userService.findByApiKeyAndSourceIp(apiKey, sourceIp);
        } else {
            return userService.findByApiKeyAndParentIsNull(apiKey);
        }
    }

    private void setUserContext(UserEntity userEntity) {
        UserContext parentContext =
                userEntity.getUserType() == UserType.SUB_CLIENT && userEntity.getParent() != null
                        ? buildUserContext(userEntity.getParent())
                        : null;
        UserContext userContext = buildUserContext(userEntity, parentContext);
        UserContextHolder.set(userContext);
    }

    private UserContext buildUserContext(UserEntity entity) {
        return buildUserContext(entity, null);
    }

    private UserContext buildUserContext(UserEntity entity, UserContext parentContext) {
        return new UserContext(
                entity.getId(),
                entity.getName(),
                entity.getUserType(),
                entity.getSourceIp(),
                getScopeFromUserType(entity.getUserType()),
                parentContext,
                entity.getResponseTemplate());
    }

    private void logUserAccess(
            UserEntity userEntity, String requestURI, String remoteIp, String sourceIp) {
        log.info(
                "Remote IP : {} , Source IP : {} ,User {} (Type: {}) accessed {}",
                remoteIp,
                sourceIp,
                userEntity.getName(),
                userEntity.getUserType(),
                requestURI);
    }

    private boolean isPublicEndpoint(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String remoteAddr = request.getRemoteAddr();

        if (uri.equals("/actuator/prometheus")
                || uri.equals("/actuator/info")
                || uri.startsWith("/actuator/health/")) {
            return remoteAddr.equals("127.0.0.1")
                    || remoteAddr.equals("::1")
                    || remoteAddr.equals("0:0:0:0:0:0:0:1");
        }

        return uri.equals("/actuator/health")
                || uri.startsWith("/swagger-ui")
                || uri.startsWith("/v3/api-docs");
    }

    private Scope getScopeFromUserType(UserType userType) {
        return switch (userType) {
            case SUB_CLIENT -> Scope.SUB_CLIENT;
            case CLIENT -> Scope.CLIENT;
            default -> Scope.GLOBAL;
        };
    }
}
