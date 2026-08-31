package com.hmg.ipmap.common.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hmg.ipmap.common.exception.GlobalErrorResponse;
import com.hmg.ipmap.common.exception.GlobalException;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Collections;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/test-path");
    }

    @Test
    void handleHttpMessageNotReadable_ShouldReturnBadRequest() {
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);
        when(ex.getMessage()).thenReturn("Invalid JSON");
        when(ex.getLocalizedMessage()).thenReturn("Invalid JSON body");
        ResponseEntity<GlobalErrorResponse> response =
                handler.handleHttpMessageNotReadable(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        GlobalErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getMessage()).contains("Malformed JSON");
        assertThat(body.getPath()).isEqualTo("/test-path");
    }

    @Test
    void handleMethodValidation_ShouldReturnValidationErrors() {
        HandlerMethodValidationException ex = mock(HandlerMethodValidationException.class);
        when(ex.getAllErrors()).thenReturn(Collections.emptyList());
        ResponseEntity<GlobalErrorResponse> response = handler.handleMethodValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Assertions.assertNotNull(response.getBody());
        assertThat(response.getBody().getMessage()).isEqualTo("Validation failed");
    }

    @Test
    void handleMissingServletRequestParameter_ShouldReturnBadRequest() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("ip", "String");
        ResponseEntity<GlobalErrorResponse> response =
                handler.handleMissingServletRequestParameter(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        GlobalErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getMessage()).contains("ip");
        assertThat(body.getPath()).isEqualTo("/test-path");
    }

    @Test
    void handleMissingRequestHeader_ShouldDelegateToGlobalException() throws NoSuchMethodException {
        Method method = this.getClass().getDeclaredMethod("dummyMethod", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);

        MissingRequestHeaderException ex =
                new MissingRequestHeaderException("X-API-KEY", parameter);
        ResponseEntity<GlobalErrorResponse> response =
                handler.handleMissingRequestHeader(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Assertions.assertNotNull(response.getBody());
        assertThat(response.getBody().getMessage())
                .contains("Required request header 'X-API-KEY'"); // ✅ Updated assertion
    }

    @SuppressWarnings("unused")
    private void dummyMethod(String header) {
        /* Dummy method for MethodParameter */
    }

    @Test
    void handleGlobalException_ShouldHandleUnauthorized() {
        GlobalException ex = new GlobalException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        ResponseEntity<GlobalErrorResponse> response = handler.handleGlobalException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        Assertions.assertNotNull(response.getBody());
    }

    @Test
    void handleGlobalException_ShouldHandleClientError() {
        GlobalException ex = new GlobalException(HttpStatus.BAD_REQUEST, "Bad Request");

        ResponseEntity<GlobalErrorResponse> response = handler.handleGlobalException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleGlobalException_ShouldHandleServerError() {
        GlobalException ex = new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error");

        ResponseEntity<GlobalErrorResponse> response = handler.handleGlobalException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void handleNullPointerException_ShouldReturnInternalServerError() {
        NullPointerException ex = new NullPointerException("Null value encountered");
        ResponseEntity<GlobalErrorResponse> response =
                handler.handleNullPointerException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        GlobalErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getMessage()).isEqualTo("An internal error occurred");
        assertThat(body.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(body.getPath()).isEqualTo("/test-path");
    }

    @Test
    void handleIllegalArgumentException_ShouldReturnBadRequest() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument value");
        ResponseEntity<GlobalErrorResponse> response =
                handler.handleIllegalArgumentException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        GlobalErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getMessage()).isEqualTo("Invalid argument provided");
        assertThat(body.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(body.getPath()).isEqualTo("/test-path");
    }

    @Test
    void handleIllegalStateException_ShouldReturnInternalServerError() {
        IllegalStateException ex = new IllegalStateException("Invalid state");
        ResponseEntity<GlobalErrorResponse> response =
                handler.handleIllegalStateException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        GlobalErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getMessage()).isEqualTo("Operation cannot be performed in current state");
        assertThat(body.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(body.getPath()).isEqualTo("/test-path");
    }

    @Test
    void handleGeneralException_ShouldReturnInternalServerError() {
        Exception ex = new Exception("Unexpected error");
        ResponseEntity<GlobalErrorResponse> response = handler.handleGeneralException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        GlobalErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getMessage()).isEqualTo("An unexpected error occurred");
        assertThat(body.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(body.getPath()).isEqualTo("/test-path");
    }
}
