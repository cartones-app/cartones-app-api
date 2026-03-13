package com.eliasgonzalez.cartones.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manejador global de excepciones que estandariza las respuestas de error
 * siguiendo RFC 7807 (Problem Details for HTTP APIs).
 * <p>
 * Características principales:
 * - Respuestas consistentes con estructura ErrorResponse
 * - Protección de información sensible en producción (sin stack traces)
 * - Logging detallado para troubleshooting
 * - Soporte para excepciones de seguridad, validación y dominio
 * <p>
 * El comportamiento se adapta según el perfil activo:
 * - dev: Incluye stack traces y detalles técnicos completos
 * - prod: Oculta detalles internos, solo mensajes genéricos
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Perfil activo de Spring (dev, prod, etc.)
     * Se usa para ajustar el nivel de detalle en las respuestas de error
     */
    @Value("${spring.profiles.active:prod}")
    private String activeProfile;

    // ===========================================
    // MÉTODOS AUXILIARES
    // ===========================================

    /**
     * Construye un ErrorResponse consistente con todos los campos RFC 7807.
     *
     * @param status Código de estado HTTP
     * @param error Título del error
     * @param message Mensaje detallado
     * @param details Lista de detalles adicionales
     * @param request Solicitud HTTP (para extraer instance)
     * @return ErrorResponse completo
     */
    private ErrorResponse buildErrorResponse(HttpStatus status, String error, String message,
                                              List<String> details, HttpServletRequest request) {
        return ErrorResponse.builder()
                .status(status.value())
                .error(error)
                .message(message)
                .details(details)
                .type(null) // Se puede extender para categorizar tipos de error
                .instance(request != null ? request.getRequestURI() : null)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Verifica si el perfil activo es de desarrollo (permite detalles técnicos).
     *
     * @return true si es perfil dev, false en caso contrario
     */
    private boolean isDevProfile() {
        return "dev".equalsIgnoreCase(activeProfile);
    }

    /**
     * Convierte el stack trace de una excepción en una lista de strings.
     * Solo se usa en perfil dev.
     *
     * @param ex Excepción
     * @return Lista con elementos del stack trace
     */
    private List<String> getStackTraceAsList(Exception ex) {
        if (!isDevProfile()) {
            return List.of();
        }
        return Arrays.stream(ex.getStackTrace())
                .limit(10) // Limitar a 10 líneas para evitar respuestas gigantes
                .map(StackTraceElement::toString)
                .collect(Collectors.toList());
    }

    // ===========================================
    // MANEJADORES DE EXCEPCIONES DE DOMINIO
    // ===========================================

    // Manejador específico para las posibles excepciones de Excel
    @ExceptionHandler(ExcelProcessingException.class)
    public ResponseEntity<ErrorResponse> handleExcelProcessingException(
            ExcelProcessingException ex, HttpServletRequest request) {

        log.error("Error de procesamiento de Excel en {}: {}", request.getRequestURI(), ex.getMessage());
        if (isDevProfile()) {
            log.debug("Detalles del error Excel:", ex);
        }

        HttpStatus status = HttpStatus.UNPROCESSABLE_ENTITY;

        ErrorResponse response = buildErrorResponse(
                status,
                "Error de Procesamiento de Excel",
                ex.getMessage(),
                ex.getErrorDetails(),
                request
        );

        return new ResponseEntity<>(response, status);
    }

    // Manejador específico para las posibles excepciones para FileProcessingException
    @ExceptionHandler(FileProcessingException.class)
    public ResponseEntity<ErrorResponse> handleFileProcessingException(
            FileProcessingException ex, HttpServletRequest request) {

        log.error("Error de procesamiento de archivo en {}: {}", request.getRequestURI(), ex.getMessage());

        HttpStatus status = HttpStatus.UNSUPPORTED_MEDIA_TYPE;

        ErrorResponse response = buildErrorResponse(
                status,
                "Error de Procesamiento de Archivo",
                ex.getMessage(),
                ex.getErrorDetails(),
                request
        );

        return new ResponseEntity<>(response, status);
    }

    // Manejador específico para las posibles excepciones para PdfCreationException
    @ExceptionHandler(PdfCreationException.class)
    public ResponseEntity<ErrorResponse> handlePdfCreationException(
            PdfCreationException ex, HttpServletRequest request) {

        log.error("Error generando PDF en {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

        // En producción, no exponer detalles internos de generación de PDF
        String message = isDevProfile()
                ? ex.getMessage()
                : "Error generando el archivo PDF. Contacte al administrador.";
        List<String> details = isDevProfile() ? ex.getErrorDetails() : List.of();

        ErrorResponse response = buildErrorResponse(
                status,
                "Error Interno del Servidor",
                message,
                details,
                request
        );

        return new ResponseEntity<>(response, status);
    }

    // Manejador específico para las posibles excepciones para UnprocessableEntityException
    @ExceptionHandler(UnprocessableEntityException.class)
    public ResponseEntity<ErrorResponse> handleUnprocessableEntityException(
            UnprocessableEntityException ex, HttpServletRequest request) {

        log.error("Estado de recurso inválido en {}: {}", request.getRequestURI(), ex.getMessage());

        HttpStatus status = HttpStatus.UNPROCESSABLE_ENTITY;

        ErrorResponse response = buildErrorResponse(
                status,
                "Estado de recurso inválido",
                ex.getMessage(),
                ex.getErrorDetails(),
                request
        );

        return new ResponseEntity<>(response, status);
    }

    // Manejador específico para las posibles excepciones para ResourceNotFoundException
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex, HttpServletRequest request) {

        log.error("Recurso no encontrado en {}: {}", request.getRequestURI(), ex.getMessage());

        HttpStatus status = HttpStatus.NOT_FOUND;

        ErrorResponse response = buildErrorResponse(
                status,
                "Recurso no encontrado",
                ex.getMessage(),
                ex.getErrorDetails(),
                request
        );

        return new ResponseEntity<>(response, status);
    }

    // ===========================================
    // MANEJADORES DE EXCEPCIONES DE VALIDACIÓN
    // ===========================================

    // Manejador para errores de validación de argumentos (@Valid en DTOs)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        log.warn("Error de validación en {}: {} campo(s) inválido(s)",
                request.getRequestURI(),
                ex.getBindingResult().getErrorCount());

        if (isDevProfile()) {
            ex.getBindingResult().getFieldErrors().forEach(error ->
                    log.debug("Campo inválido: {} - {}", error.getField(), error.getDefaultMessage())
            );
        }

        HttpStatus status = HttpStatus.BAD_REQUEST;

        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.toList());

        ErrorResponse response = buildErrorResponse(
                status,
                "Error de Validación",
                "Uno o más campos de la solicitud no son válidos.",
                errors,
                request
        );

        return new ResponseEntity<>(response, status);
    }

    // Manejador para violaciones de restricciones (@Validated en parámetros de método)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException ex, HttpServletRequest request) {

        log.warn("Violación de restricción en {}: {}", request.getRequestURI(), ex.getMessage());

        HttpStatus status = HttpStatus.BAD_REQUEST;

        List<String> errors = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toList());

        ErrorResponse response = buildErrorResponse(
                status,
                "Error de Validación de Parámetros",
                "Los parámetros de la solicitud no cumplen con las restricciones requeridas.",
                errors,
                request
        );

        return new ResponseEntity<>(response, status);
    }

    // Manejador para cuando falta un archivo MultipartFile
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestPartException(
            MissingServletRequestPartException ex, HttpServletRequest request) {

        log.error("Archivo requerido faltante en {}: {}", request.getRequestURI(), ex.getRequestPartName());

        HttpStatus status = HttpStatus.BAD_REQUEST;

        String detailMessage = isDevProfile()
                ? ex.getMessage()
                : "Se esperaba un archivo con el nombre: " + ex.getRequestPartName();

        ErrorResponse response = buildErrorResponse(
                status,
                "Archivo Requerido Faltante",
                "Se esperaba un archivo, pero no se encontró en la solicitud.",
                List.of(detailMessage),
                request
        );

        return new ResponseEntity<>(response, status);
    }

    // ===========================================
    // MANEJADORES DE EXCEPCIONES DE SEGURIDAD
    // ===========================================

    // Manejador para acceso denegado (usuario autenticado sin permisos suficientes)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {

        log.warn("Acceso denegado en {}: {}", request.getRequestURI(), ex.getMessage());

        HttpStatus status = HttpStatus.FORBIDDEN;

        ErrorResponse response = buildErrorResponse(
                status,
                "Acceso Denegado",
                "No tienes permisos suficientes para acceder a este recurso.",
                List.of(),
                request
        );

        return new ResponseEntity<>(response, status);
    }

    // Manejador para credenciales inválidas
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(
            BadCredentialsException ex, HttpServletRequest request) {

        log.warn("Intento de autenticación fallido en {}", request.getRequestURI());

        HttpStatus status = HttpStatus.UNAUTHORIZED;

        ErrorResponse response = buildErrorResponse(
                status,
                "Credenciales Inválidas",
                "Las credenciales proporcionadas son incorrectas.",
                List.of(),
                request
        );

        return new ResponseEntity<>(response, status);
    }

    // Manejador general para errores de autenticación (incluye JWT inválido/expirado)
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {

        log.warn("Error de autenticación en {}: {}", request.getRequestURI(), ex.getMessage());

        HttpStatus status = HttpStatus.UNAUTHORIZED;

        // En producción, no exponer detalles sobre por qué falló la autenticación (seguridad)
        String message = isDevProfile()
                ? "Error de autenticación: " + ex.getMessage()
                : "No estás autenticado o tu token es inválido/expirado.";

        ErrorResponse response = buildErrorResponse(
                status,
                "Error de Autenticación",
                message,
                List.of(),
                request
        );

        return new ResponseEntity<>(response, status);
    }

    // ===========================================
    // MANEJADOR GENERAL (CATCH-ALL)
    // ===========================================

    /**
     * Manejador para todas las excepciones no capturadas por handlers específicos.
     * <p>
     * CRÍTICO: Este handler protege información sensible en producción.
     * - En dev: Retorna mensaje de excepción y stack trace limitado (troubleshooting)
     * - En prod: Retorna mensaje genérico sin detalles técnicos (seguridad)
     * <p>
     * Siempre registra el error completo en logs para análisis posterior.
     *
     * @param ex Excepción no manejada
     * @param request Solicitud HTTP
     * @return ResponseEntity con ErrorResponse genérico
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllExceptions(
            Exception ex, HttpServletRequest request) {

        // SIEMPRE registrar el error completo en logs (incluso en producción)
        log.error("Error inesperado en {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

        // Protección de información sensible según el perfil
        String message;
        List<String> details;

        if (isDevProfile()) {
            // Desarrollo: Exponer detalles técnicos para debugging
            message = "Error interno: " + ex.getMessage();
            details = getStackTraceAsList(ex);
            log.debug("Tipo de excepción: {}", ex.getClass().getName());
        } else {
            // Producción: Mensaje genérico sin detalles técnicos
            message = "Ocurrió un error inesperado. El equipo técnico ha sido notificado.";
            details = List.of();
            // En producción, el stack trace solo está en logs, no en la respuesta HTTP
        }

        ErrorResponse response = buildErrorResponse(
                status,
                "Error Interno del Servidor",
                message,
                details,
                request
        );

        return new ResponseEntity<>(response, status);
    }
}