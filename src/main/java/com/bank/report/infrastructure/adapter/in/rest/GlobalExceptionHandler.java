package com.bank.report.infrastructure.adapter.in.rest;

import com.bank.report.domain.exception.DownstreamServiceUnavailableException;
import com.bank.report.domain.exception.InvalidRangeException;
import com.bank.report.domain.exception.NotAvailableException;
import com.bank.report.domain.exception.ProductNotFoundException;
import com.bank.report.domain.exception.RangeTooLargeException;
import com.bank.report.infrastructure.adapter.in.rest.dto.ErrorResponse;
import com.bank.report.infrastructure.adapter.in.rest.dto.FieldError;
import jakarta.validation.ConstraintViolationException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

/**
 * Excepciones → cuerpo estándar {@code { timestamp, status, code, message, path }} (data-model §6):
 * 400 VALIDATION_ERROR / INVALID_RANGE, 404 PRODUCT_NOT_FOUND, 422 RANGE_TOO_LARGE,
 * 501 NOT_AVAILABLE, 503 SERVICE_UNAVAILABLE.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String VALIDATION_ERROR = "VALIDATION_ERROR";

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ProductNotFoundException ex, ServerWebExchange exchange) {
        return build(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", ex.getMessage(), exchange, null);
    }

    @ExceptionHandler(InvalidRangeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRange(InvalidRangeException ex, ServerWebExchange exchange) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_RANGE", ex.getMessage(), exchange, null);
    }

    @ExceptionHandler(RangeTooLargeException.class)
    public ResponseEntity<ErrorResponse> handleRangeTooLarge(RangeTooLargeException ex, ServerWebExchange exchange) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "RANGE_TOO_LARGE", ex.getMessage(), exchange, null);
    }

    @ExceptionHandler(NotAvailableException.class)
    public ResponseEntity<ErrorResponse> handleNotAvailable(NotAvailableException ex, ServerWebExchange exchange) {
        return build(HttpStatus.NOT_IMPLEMENTED, "NOT_AVAILABLE", ex.getMessage(), exchange, null);
    }

    @ExceptionHandler(DownstreamServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleUnavailable(DownstreamServiceUnavailableException ex,
                                                           ServerWebExchange exchange) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", ex.getMessage(), exchange, null);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleParamValidation(HandlerMethodValidationException ex,
                                                               ServerWebExchange exchange) {
        return build(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, ex.getReason(), exchange, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException ex,
                                                          ServerWebExchange exchange) {
        List<FieldError> details = ex.getConstraintViolations().stream()
                .map(violation -> {
                    String path = violation.getPropertyPath().toString();
                    return fieldError(path.substring(path.lastIndexOf('.') + 1), violation.getMessage());
                })
                .toList();
        return build(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, "Validation failed", exchange, details);
    }

    /** Parámetro ausente o con formato inválido (una fecha mal escrita, una categoría desconocida). */
    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ErrorResponse> handleInput(ServerWebInputException ex, ServerWebExchange exchange) {
        return build(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, ex.getReason(), exchange, null);
    }

    /** {@code productType} fuera de los cuatro valores, página o {@code limit} fuera de rango. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex,
                                                               ServerWebExchange exchange) {
        return build(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, ex.getMessage(), exchange, null);
    }

    private static FieldError fieldError(String field, String message) {
        FieldError detail = new FieldError();
        detail.setField(field);
        detail.setMessage(message);
        return detail;
    }

    private static ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message,
                                                       ServerWebExchange exchange, List<FieldError> details) {
        ErrorResponse body = new ErrorResponse();
        body.setTimestamp(OffsetDateTime.now());
        body.setStatus(status.value());
        body.setCode(code);
        body.setMessage(message);
        body.setPath(exchange.getRequest().getPath().value());
        body.setDetails(details);
        return ResponseEntity.status(status).body(body);
    }
}
