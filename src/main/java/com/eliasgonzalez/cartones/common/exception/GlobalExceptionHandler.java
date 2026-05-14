package com.eliasgonzalez.cartones.common.exception;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import com.eliasgonzalez.cartones.common.logging.LogSanitizer;

import io.github.eliasss3990.openflags.core.OpenFlagsClient;
import lombok.extern.slf4j.Slf4j;

/**
 * Manejador global de excepciones que estandariza las respuestas de error
 * siguiendo RFC 7807 (Problem Details for HTTP APIs).
 *
 * <p><b>openflags</b>: usa {@link OpenFlagsClient} para evaluar el flag
 * {@code excel.expose-error-details} y decidir si los detalles de validación
 * de Excel se exponen en el campo {@code details} de la respuesta 422.
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** Flag key: cuando true, las respuestas 422 de Excel incluyen el detalle de errores. */
    private static final String FLAG_EXCEL_EXPOSE_ERROR_DETAILS = "excel.expose-error-details";

    @Value("${spring.profiles.active:prod}")
    private String activeProfile;

    private final OpenFlagsClient flags;

    public GlobalExceptionHandler(OpenFlagsClient flags) {
        this.flags = flags;
    }

    private ErrorResponse buildErrorResponse(
            HttpStatus status, String error, String message, List<String> details, HttpServletRequest request) {
        return ErrorResponse.builder()
                .status(status.value())
                .error(error)
                .message(message)
                .details(details)
                .type(null)
                .instance(request != null ? request.getRequestURI() : null)
                .timestamp(LocalDateTime.now())
                .build();
    }

    private boolean isDevProfile() {
        return "dev".equalsIgnoreCase(activeProfile);
    }

    private List<String> getStackTraceAsList(Exception ex) {
        if (!isDevProfile()) {
            return List.of();
        }
        return Arrays.stream(ex.getStackTrace())
                .limit(10)
                .map(StackTraceElement::toString)
                .collect(Collectors.toList());
    }

    @ExceptionHandler(ExcelProcessingException.class)
    public ResponseEntity<ErrorResponse> handleExcelProcessingException(
            ExcelProcessingException ex, HttpServletRequest request) {
        log.error(
                "Error de procesamiento de Excel en {}: {}",
                LogSanitizer.safe(request.getRequestURI()),
                LogSanitizer.safe(ex.getMessage()));
        log.debug("Detalles del error Excel:", ex);
        // flag excel.expose-error-details: si false, los detalles quedan en logs
        // pero no se exponen al cliente. Default true (decisión 2026-05-09 del
        // usuario: los detalles son user-facing por diseño — el frontend muestra
        // "fila X / vendedor Y inválido" para que el operador corrija su Excel).
        boolean exposeDetails = flags.getBooleanValue(FLAG_EXCEL_EXPOSE_ERROR_DETAILS, true);
        List<String> details = exposeDetails ? ex.getErrorDetails() : List.of();
        ErrorResponse response = buildErrorResponse(
                HttpStatus.UNPROCESSABLE_ENTITY, "Error de Procesamiento de Excel", ex.getMessage(), details, request);
        return new ResponseEntity<>(response, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(FileProcessingException.class)
    public ResponseEntity<ErrorResponse> handleFileProcessingException(
            FileProcessingException ex, HttpServletRequest request) {
        log.error(
                "Error de procesamiento de archivo en {}: {}",
                LogSanitizer.safe(request.getRequestURI()),
                LogSanitizer.safe(ex.getMessage()));
        ErrorResponse response = buildErrorResponse(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Error de Procesamiento de Archivo",
                ex.getMessage(),
                ex.getErrorDetails(),
                request);
        return new ResponseEntity<>(response, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(PdfCreationException.class)
    public ResponseEntity<ErrorResponse> handlePdfCreationException(
            PdfCreationException ex, HttpServletRequest request) {
        log.error(
                "Error generando PDF en {}: {}",
                LogSanitizer.safe(request.getRequestURI()),
                LogSanitizer.safe(ex.getMessage()),
                ex);
        String message =
                isDevProfile() ? ex.getMessage() : "Error generando el archivo PDF. Contacte al administrador.";
        List<String> details = isDevProfile() ? ex.getErrorDetails() : List.of();
        ErrorResponse response = buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR, "Error Interno del Servidor", message, details, request);
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(UnprocessableEntityException.class)
    public ResponseEntity<ErrorResponse> handleUnprocessableEntityException(
            UnprocessableEntityException ex, HttpServletRequest request) {
        log.error(
                "Estado de recurso inválido en {}: {}",
                LogSanitizer.safe(request.getRequestURI()),
                LogSanitizer.safe(ex.getMessage()));
        ErrorResponse response = buildErrorResponse(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Estado de recurso inválido",
                ex.getMessage(),
                ex.getErrorDetails(),
                request);
        return new ResponseEntity<>(response, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex, HttpServletRequest request) {
        log.error(
                "Recurso no encontrado en {}: {}",
                LogSanitizer.safe(request.getRequestURI()),
                LogSanitizer.safe(ex.getMessage()));
        ErrorResponse response = buildErrorResponse(
                HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage(), ex.getErrorDetails(), request);
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn(
                "Error de validación en {}: {} campo(s) inválido(s)",
                LogSanitizer.safe(request.getRequestURI()),
                ex.getBindingResult().getErrorCount());
        if (isDevProfile()) {
            ex.getBindingResult()
                    .getFieldErrors()
                    .forEach(
                            error -> log.debug("Campo inválido: {} - {}", error.getField(), error.getDefaultMessage()));
        }
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.toList());
        ErrorResponse response = buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Error de Validación",
                "Uno o más campos de la solicitud no son válidos.",
                errors,
                request);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException ex, HttpServletRequest request) {
        log.warn(
                "Violación de restricción en {}: {}",
                LogSanitizer.safe(request.getRequestURI()),
                LogSanitizer.safe(ex.getMessage()));
        List<String> errors = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toList());
        ErrorResponse response = buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Error de Validación de Parámetros",
                "Los parámetros de la solicitud no cumplen con las restricciones requeridas.",
                errors,
                request);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestPartException(
            MissingServletRequestPartException ex, HttpServletRequest request) {
        log.error(
                "Archivo requerido faltante en {}: {}",
                LogSanitizer.safe(request.getRequestURI()),
                LogSanitizer.safe(ex.getRequestPartName()));
        String detailMessage =
                isDevProfile() ? ex.getMessage() : "Se esperaba un archivo con el nombre: " + ex.getRequestPartName();
        ErrorResponse response = buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Archivo Requerido Faltante",
                "Se esperaba un archivo, pero no se encontró en la solicitud.",
                List.of(detailMessage),
                request);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn(
                "Acceso denegado en {}: {}",
                LogSanitizer.safe(request.getRequestURI()),
                LogSanitizer.safe(ex.getMessage()));
        ErrorResponse response = buildErrorResponse(
                HttpStatus.FORBIDDEN,
                "Acceso Denegado",
                "No tienes permisos suficientes para acceder a este recurso.",
                List.of(),
                request);
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(
            BadCredentialsException ex, HttpServletRequest request) {
        log.warn("Intento de autenticación fallido en {}", LogSanitizer.safe(request.getRequestURI()));
        ErrorResponse response = buildErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "Credenciales Inválidas",
                "Las credenciales proporcionadas son incorrectas.",
                List.of(),
                request);
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {
        log.warn(
                "Error de autenticación en {}: {}",
                LogSanitizer.safe(request.getRequestURI()),
                LogSanitizer.safe(ex.getMessage()));
        String message = isDevProfile()
                ? "Error de autenticación: " + ex.getMessage()
                : "No estás autenticado o tu token es inválido/expirado.";
        ErrorResponse response =
                buildErrorResponse(HttpStatus.UNAUTHORIZED, "Error de Autenticación", message, List.of(), request);
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn(
                "Violación de integridad en {}: {}",
                LogSanitizer.safe(request.getRequestURI()),
                LogSanitizer.safe(ex.getMostSpecificCause().getMessage()));
        log.debug("Detalle de DataIntegrityViolationException:", ex);
        String message = isDevProfile()
                ? "Conflicto de integridad: " + ex.getMostSpecificCause().getMessage()
                : "El recurso entra en conflicto con datos existentes (duplicado o referencia inválida).";
        ErrorResponse response =
                buildErrorResponse(HttpStatus.CONFLICT, "Conflicto de Integridad", message, List.of(), request);
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.warn(
                "Tamaño máximo de archivo excedido en {}: {} bytes",
                LogSanitizer.safe(request.getRequestURI()),
                ex.getMaxUploadSize());
        log.debug("Detalle de MaxUploadSizeExceededException:", ex);
        ErrorResponse response = buildErrorResponse(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Archivo demasiado grande",
                "El archivo enviado supera el tamaño máximo permitido.",
                List.of(),
                request);
        return new ResponseEntity<>(response, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllExceptions(Exception ex, HttpServletRequest request) {
        log.error(
                "Error inesperado en {}: {}",
                LogSanitizer.safe(request.getRequestURI()),
                LogSanitizer.safe(ex.getMessage()),
                ex);
        String message;
        List<String> details;
        if (isDevProfile()) {
            message = "Error interno: " + ex.getMessage();
            details = getStackTraceAsList(ex);
            log.debug("Tipo de excepción: {}", ex.getClass().getName());
        } else {
            message = "Ocurrió un error inesperado. El equipo técnico ha sido notificado.";
            details = List.of();
        }
        ErrorResponse response = buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR, "Error Interno del Servidor", message, details, request);
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
