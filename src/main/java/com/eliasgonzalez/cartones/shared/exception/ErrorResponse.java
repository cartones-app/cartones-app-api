package com.eliasgonzalez.cartones.shared.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO para respuestas de error estandarizadas siguiendo RFC 7807 (Problem Details for HTTP APIs).
 * <p>
 * RFC 7807 define un formato estándar para describir problemas en APIs HTTP, permitiendo
 * que los clientes manejen errores de manera consistente y predecible.
 * <p>
 * Estructura compatible con RFC 7807 pero manteniendo compatibilidad hacia atrás:
 * - type: URI que identifica el tipo de problema (opcional, para categorizar errores)
 * - title: Resumen corto del problema (campo 'error')
 * - status: Código de estado HTTP
 * - detail: Explicación específica (campo 'message')
 * - instance: URI de la solicitud que causó el error (opcional)
 * - timestamp: Momento en que ocurrió el error
 * - details: Lista de errores específicos para validaciones (extensión custom)
 * <p>
 * Los campos opcionales se omiten del JSON si son null (@JsonInclude.NON_NULL)
 */
@AllArgsConstructor
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /**
     * Código de estado HTTP (ejemplo: 400, 404, 500)
     */
    private final int status;

    /**
     * Resumen corto del tipo de error (RFC 7807: "title")
     */
    private final String error;

    /**
     * Descripción detallada del error específico (RFC 7807: "detail")
     */
    private final String message;

    /**
     * Lista de errores específicos (útil para validaciones)
     * Extensión custom no contemplada en RFC 7807
     */
    private final List<String> details;

    /**
     * URI que identifica el tipo de problema (RFC 7807: "type")
     * Ejemplo: "https://api.cartones.com/errors/validation-error"
     * Se omite si es null
     */
    private final String type;

    /**
     * URI de la solicitud que causó el error (RFC 7807: "instance")
     * Ejemplo: "/api/vendedores/carga"
     * Se omite si es null
     */
    private final String instance;

    /**
     * Timestamp del momento en que ocurrió el error
     * Formato ISO 8601: "2026-02-10T14:30:00"
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime timestamp;

}