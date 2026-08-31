package com.hmg.ipmap.common.handler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hmg.ipmap.common.enums.UserType;
import com.hmg.ipmap.user.UserEntity;
import com.hmg.ipmap.user.UserServiceImpl;
import com.hmg.ipmap.user.exception.AccessDeniedException;
import com.hmg.ipmap.user.exception.ApiKeyUnauthorizeException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTest {

    @Mock HandlerExceptionResolver handlerExceptionResolver;

    @Mock UserServiceImpl userService;

    ApiKeyAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthenticationFilter(handlerExceptionResolver, userService);
    }

    static class RecordingFilterChain implements FilterChain {
        boolean proceeded = false;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            proceeded = true;
        }
    }

    private HttpServletRequest buildRequest(String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        return request;
    }

    private HttpServletResponse buildResponse() {
        return mock(HttpServletResponse.class);
    }

    @DisplayName(
            "Public endpoints: filter can't procces (shouldNotFilter=true) and the chain continues")
    @ParameterizedTest(name = "URI: {0}")
    @ValueSource(strings = {"/swagger-ui/index.html", "/v3/api-docs", "/actuator/health"})
    void publicEndpoints_areNotFiltered_andChainProceeds(String uri) throws Exception {
        HttpServletRequest request = buildRequest(uri);
        HttpServletResponse response = buildResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertTrue(chain.proceeded, "The filter chain must be continued for the public endpoint");
        verify(handlerExceptionResolver, never()).resolveException(any(), any(), any(), any());
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName(
            "Non-public: header API key is missing -> ApiKeyUnauthorizeException handled, chain not continued")
    void nonPublic_missingApiKey_resolvesException_andStopsChain() throws Exception {
        HttpServletRequest request = buildRequest("/secure/resource");
        // header null
        when(request.getHeader("X-HMGIPMAP-APIKEY")).thenReturn(null);
        HttpServletResponse response = buildResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        when(handlerExceptionResolver.resolveException(any(), any(), any(), any()))
                .thenReturn(null);

        filter.doFilter(request, response, chain);

        assertFalse(chain.proceeded, "The chain should not proceed when the API key is missing");
        verify(handlerExceptionResolver, times(1))
                .resolveException(
                        eq(request),
                        eq(response),
                        isNull(),
                        argThat(ApiKeyUnauthorizeException.class::isInstance));
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName(
            "Non-public: header API key is an empty string -> ApiKeyUnauthorizeException handled, chain not continued")
    void nonPublic_emptyApiKey_resolvesException_andStopsChain() throws Exception {
        HttpServletRequest request = buildRequest("/secure/resource");
        // header empty string
        when(request.getHeader("X-HMGIPMAP-APIKEY")).thenReturn("");
        HttpServletResponse response = buildResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        when(handlerExceptionResolver.resolveException(any(), any(), any(), any()))
                .thenReturn(null);

        filter.doFilter(request, response, chain);

        assertFalse(chain.proceeded, "The chain should not proceed when the API key is missing");
        verify(handlerExceptionResolver, times(1))
                .resolveException(
                        eq(request),
                        eq(response),
                        isNull(),
                        argThat(ApiKeyUnauthorizeException.class::isInstance));
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName(
            "Non-public: canAccess=false -> AccessDeniedException be handle, chain not continues")
    void nonPublic_canAccessFalse_resolvesAccessDenied_andStopsChain() throws Exception {
        HttpServletRequest request = buildRequest("/secure/resource");
        when(request.getHeader("X-HMGIPMAP-APIKEY")).thenReturn("apikey-123");
        HttpServletResponse response = buildResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        com.hmg.ipmap.common.enums.UserType userType =
                mock(com.hmg.ipmap.common.enums.UserType.class);
        when(userType.canAccess("/secure/resource")).thenReturn(false);

        UserEntity userEntity = mock(UserEntity.class);
        when(userEntity.getUserType()).thenReturn(userType);

        when(userService.findByApiKeyAndParentIsNull("apikey-123")).thenReturn(userEntity);

        when(handlerExceptionResolver.resolveException(any(), any(), any(), any()))
                .thenReturn(null);

        filter.doFilter(request, response, chain);

        assertFalse(chain.proceeded, "The chain must stop if access is denied");
        verify(handlerExceptionResolver, times(1))
                .resolveException(
                        eq(request),
                        eq(response),
                        isNull(),
                        argThat(AccessDeniedException.class::isInstance));

        verify(userEntity, times(1)).getUserType();
        verify(userType, times(1)).canAccess("/secure/resource");
    }

    @Test
    @DisplayName("Non-public: Access Success → set UserContext and proceed chain")
    void nonPublic_success_setsContext_andProceedsChain() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/secure/resource");
        request.setServletPath("/secure/resource");

        request.addHeader("X-HMGIPMAP-APIKEY", "apikey-123");

        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        UserType mockType = mock(UserType.class);
        when(mockType.canAccess("/secure/resource")).thenReturn(true);

        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(7L);
        when(user.getUserType()).thenReturn(mockType);

        when(userService.findByApiKeyAndParentIsNull("apikey-123")).thenReturn(user);

        filter.doFilter(request, response, chain);

        assertTrue(chain.proceeded, "Filter MUST proceed when authentication & access succeed");
        verify(handlerExceptionResolver, never()).resolveException(any(), any(), any(), any());

        verify(mockType).canAccess("/secure/resource");
    }

    @Test
    @DisplayName("The chain must continue if access is successful ...")
    void shouldNotFilter_returnsTrue_forPublicUris() {
        ApiKeyAuthenticationFilter f = filter;
        assertTrue(f.shouldNotFilter(buildRequest("/actuator/health")));
        assertTrue(f.shouldNotFilter(buildRequest("/swagger-ui/index.html")));
        assertTrue(f.shouldNotFilter(buildRequest("/v3/api-docs")));

        assertFalse(f.shouldNotFilter(buildRequest("/secure/resource")));
    }
}
