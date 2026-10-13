package com.bankSimulate.infrastructure.web;

import com.bankSimulate.domain.common.ApiException;
import com.bankSimulate.infrastructure.logging.RequestIdFilter;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Problem> api(ApiException e) {
        if (e.getStatus() >= 500) log.warn("Rejected status={} code={}: {}", e.getStatus(), e.getCode(), e.getMessage());
        else log.info("Rejected status={} code={}: {}", e.getStatus(), e.getCode(), e.getMessage());
        return error(e.getStatus(), e.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<Problem> badRequest(Exception e) {
        return error(400, "INVALID_REQUEST", "Request is invalid");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Problem> missingHeader(MissingRequestHeaderException e) {
        return error(400, "MISSING_HEADER", "Missing required header: " + e.getHeaderName());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Problem> missingParameter(MissingServletRequestParameterException e) {
        return error(400, "MISSING_PARAMETER", "Missing required parameter: " + e.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Problem> typeMismatch(MethodArgumentTypeMismatchException e) {
        return error(400, "INVALID_PARAMETER_TYPE", "Invalid value for parameter: " + e.getName());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Problem> unsupportedMediaType(HttpMediaTypeNotSupportedException e) {
        return error(415, "UNSUPPORTED_MEDIA_TYPE", "Content-Type is not supported");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Problem> methodNotAllowed(HttpRequestMethodNotSupportedException e) {
        HttpHeaders headers = new HttpHeaders();
        if (e.getSupportedHttpMethods() != null) headers.setAllow(e.getSupportedHttpMethods());
        return error(405, "METHOD_NOT_ALLOWED", "Method " + e.getMethod() + " is not allowed", headers);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Problem> notFound(NoResourceFoundException e) {
        return error(404, "RESOURCE_NOT_FOUND", "Resource not found");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Problem> unexpected(Exception e) {
        log.error("Unhandled request failure traceId={}", MDC.get(RequestIdFilter.MDC_KEY), e);
        return error(500, "INTERNAL_ERROR", "Internal server error");
    }

    private ResponseEntity<Problem> error(int status, String code, String title) {
        return error(status, code, title, new HttpHeaders());
    }

    private ResponseEntity<Problem> error(int status, String code, String title, HttpHeaders headers) {
        return ResponseEntity.status(status).headers(headers)
                .contentType(MediaType.valueOf("application/problem+json"))
                .body(new Problem("about:blank", title, status, code, MDC.get(RequestIdFilter.MDC_KEY)));
    }

    public record Problem(String type, String title, int status, String code, String traceId) {}
}
