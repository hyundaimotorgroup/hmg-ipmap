package com.hmg.ipmap.common.handler;

import com.hmg.ipmap.common.exception.GlobalErrorResponse;
import com.hmg.ipmap.common.exception.GlobalException;
import com.hmg.ipmap.common.util.DateUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.ZonedDateTime;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<GlobalErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception, HttpServletRequest request) {

        log.debug(
                "Failed to parse request body: {}. Path: {}",
                exception.getMessage(),
                request.getRequestURI());

        HttpStatus status = HttpStatus.BAD_REQUEST;

        return ResponseEntity.status(status)
                .body(
                        GlobalErrorResponse.builder()
                                .timestamp(ZonedDateTime.now().format(DateUtil.FORMATTER))
                                .status(status.value())
                                .error(status.getReasonPhrase())
                                .message(
                                        "Malformed JSON request. Please check the request body format.")
                                .path(request.getRequestURI())
                                .build());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<GlobalErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception, HttpServletRequest request) {

        String message =
                exception.getConstraintViolations().stream()
                        .map(ConstraintViolation::getMessage)
                        .collect(Collectors.joining(", "));

        log.debug("Constraint violation at path: {}. {}", request.getRequestURI(), message);

        HttpStatus status = HttpStatus.BAD_REQUEST;

        return ResponseEntity.status(status)
                .body(
                        GlobalErrorResponse.builder()
                                .timestamp(ZonedDateTime.now().format(DateUtil.FORMATTER))
                                .status(status.value())
                                .error(status.getReasonPhrase())
                                .message(message)
                                .path(request.getRequestURI())
                                .build());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<GlobalErrorResponse> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {

        log.warn(
                "Unsupported media type: {}. Path: {}",
                exception.getMessage(),
                request.getRequestURI());

        HttpStatus status = HttpStatus.BAD_REQUEST;

        return ResponseEntity.status(status)
                .body(
                        GlobalErrorResponse.builder()
                                .timestamp(ZonedDateTime.now().format(DateUtil.FORMATTER))
                                .status(status.value())
                                .error(status.getReasonPhrase())
                                .message(
                                        "Unsupported media type. Use Content-Type: application/json")
                                .path(request.getRequestURI())
                                .build());
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<GlobalErrorResponse> handleMethodValidation(
            HandlerMethodValidationException exception, HttpServletRequest request) {

        log.debug("Validation failed for method arguments: {}", exception.getMessage());

        HttpStatus status = HttpStatus.BAD_REQUEST;

        GlobalErrorResponse errorResponse =
                GlobalErrorResponse.builder()
                        .status(status.value())
                        .error(status.getReasonPhrase())
                        .message("Validation failed")
                        .path(request.getRequestURI())
                        .timestamp(ZonedDateTime.now().format(DateUtil.FORMATTER))
                        .build();

        return ResponseEntity.status(status).body(errorResponse);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<GlobalErrorResponse> handleMissingServletRequestParameter(
            MissingServletRequestParameterException exception, HttpServletRequest request) {

        log.debug("Missing request parameter. {}", exception.getMessage());

        GlobalException globalException =
                new GlobalException(HttpStatus.BAD_REQUEST, exception.getMessage());

        return handleGlobalException(globalException, request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<GlobalErrorResponse> handleMissingRequestHeader(
            MissingRequestHeaderException exception, HttpServletRequest request) {

        log.debug(
                "Caught MissingRequestHeaderException, converting to GlobalException. Header: {}",
                exception.getHeaderName());

        GlobalException globalException =
                new GlobalException(HttpStatus.BAD_REQUEST, exception.getMessage());

        return handleGlobalException(globalException, request);
    }

    @ExceptionHandler(GlobalException.class)
    public ResponseEntity<GlobalErrorResponse> handleGlobalException(
            GlobalException exception, HttpServletRequest request) {

        if (exception.getStatus().is5xxServerError()) {
            log.error(
                    "Server error [{} {}] at path: {}",
                    exception.getStatus().value(),
                    exception.getStatus().getReasonPhrase(),
                    request.getRequestURI(),
                    exception);
        }

        return ResponseEntity.status(exception.getStatus())
                .body(
                        GlobalErrorResponse.builder()
                                .status(exception.getStatus().value())
                                .error(exception.getStatus().getReasonPhrase())
                                .message(exception.getMessage())
                                .path(request.getRequestURI())
                                .timestamp(ZonedDateTime.now().format(DateUtil.FORMATTER))
                                .build());
    }

    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<GlobalErrorResponse> handleNullPointerException(
            NullPointerException exception, HttpServletRequest request) {

        log.error(
                "NullPointerException occurred at path: {}. Message: {}",
                request.getRequestURI(),
                exception.getMessage(),
                exception);

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

        return ResponseEntity.status(status)
                .body(
                        GlobalErrorResponse.builder()
                                .timestamp(ZonedDateTime.now().format(DateUtil.FORMATTER))
                                .status(status.value())
                                .error(status.getReasonPhrase())
                                .message("An internal error occurred")
                                .path(request.getRequestURI())
                                .build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<GlobalErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException exception, HttpServletRequest request) {

        log.error(
                "IllegalArgumentException at path: {}. Message: {}",
                request.getRequestURI(),
                exception.getMessage(),
                exception);

        HttpStatus status = HttpStatus.BAD_REQUEST;

        return ResponseEntity.status(status)
                .body(
                        GlobalErrorResponse.builder()
                                .timestamp(ZonedDateTime.now().format(DateUtil.FORMATTER))
                                .status(status.value())
                                .error(status.getReasonPhrase())
                                .message("Invalid argument provided")
                                .path(request.getRequestURI())
                                .build());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<GlobalErrorResponse> handleIllegalStateException(
            IllegalStateException exception, HttpServletRequest request) {

        log.error(
                "IllegalStateException at path: {}. Message: {}",
                request.getRequestURI(),
                exception.getMessage());

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

        return ResponseEntity.status(status)
                .body(
                        GlobalErrorResponse.builder()
                                .timestamp(ZonedDateTime.now().format(DateUtil.FORMATTER))
                                .status(status.value())
                                .error(status.getReasonPhrase())
                                .message("Operation cannot be performed in current state")
                                .path(request.getRequestURI())
                                .build());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<GlobalErrorResponse> handleNoResourceFound(
            NoResourceFoundException exception, HttpServletRequest request) {

        log.debug(
                "No resource found at path: {}. {}",
                request.getRequestURI(),
                exception.getMessage());

        HttpStatus status = HttpStatus.NOT_FOUND;

        return ResponseEntity.status(status)
                .body(
                        GlobalErrorResponse.builder()
                                .timestamp(ZonedDateTime.now().format(DateUtil.FORMATTER))
                                .status(status.value())
                                .error(status.getReasonPhrase())
                                .message("Resource not found")
                                .path(request.getRequestURI())
                                .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GlobalErrorResponse> handleGeneralException(
            Exception exception, HttpServletRequest request) {

        log.error(
                "Unexpected exception at path: {}. Type: {}. Message: {}",
                request.getRequestURI(),
                exception.getClass().getSimpleName(),
                exception.getMessage(),
                exception);

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

        return ResponseEntity.status(status)
                .body(
                        GlobalErrorResponse.builder()
                                .timestamp(ZonedDateTime.now().format(DateUtil.FORMATTER))
                                .status(status.value())
                                .error(status.getReasonPhrase())
                                .message("An unexpected error occurred")
                                .path(request.getRequestURI())
                                .build());
    }
}
