package com.eliasgonzalez.cartones.pdftemplate.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eliasgonzalez.cartones.common.exception.ResourceNotFoundException;
import com.eliasgonzalez.cartones.common.exception.UnprocessableEntityException;
import com.eliasgonzalez.cartones.common.logging.LogSanitizer;
import com.eliasgonzalez.cartones.pdftemplate.controller.dto.PdfTemplateCreateDTO;
import com.eliasgonzalez.cartones.pdftemplate.controller.dto.PdfTemplateUpdateDTO;
import com.eliasgonzalez.cartones.pdftemplate.domain.PdfTemplate;
import com.eliasgonzalez.cartones.pdftemplate.domain.enums.PdfTemplateTipo;
import com.eliasgonzalez.cartones.pdftemplate.repository.PdfTemplateRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service para gestionar templates de PDF.
 *
 * <p>
 * <b>Validación de schemaJson</b>: parseamos el JSON y verificamos que tiene
 * las keys top-level {@code basePdf} y {@code schemas}. No validamos contra el
 * schema completo de pdfme — ese costo/valor no compensa: si el JSON está mal,
 * el Designer falla al renderizarlo y el admin lo arregla.
 *
 * <p>
 * <b>Activación</b>: atómica dentro de una transacción. Desactivamos todos
 * los del mismo tipo excepto el target, luego marcamos el target como activo.
 * El índice parcial único de V6 enforza que en cualquier momento hay ≤1 activo
 * por tipo.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PdfTemplateService {

    private final PdfTemplateRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PdfTemplate obtenerActivo(PdfTemplateTipo tipo) {
        return repository.findByTipoAndActivoTrue(tipo).orElseThrow(() -> new ResourceNotFoundException(
                "No hay template activo de tipo " + tipo, List.of()));
    }

    @Transactional(readOnly = true)
    public List<PdfTemplate> listar() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public PdfTemplate obtener(String id) {
        return buscarOLanzar(id);
    }

    /**
     * Helper no-transactional usado desde los métodos write para evitar
     * self-calls a un método {@code @Transactional}. Si el caller
     * está en una tx, esta lectura se hace dentro de ella; si no, va sin tx
     * — aceptable porque solo es un {@code findById}.
     */
    private PdfTemplate buscarOLanzar(String id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException(
                "Template no encontrado: " + id, List.of()));
    }

    @Transactional
    public PdfTemplate crear(PdfTemplateCreateDTO dto) {
        validarSchemaJson(dto.schemaJson());
        PdfTemplate template = PdfTemplate.builder()
                .tipo(dto.tipo())
                .nombre(dto.nombre())
                .schemaJson(dto.schemaJson())
                .activo(false)
                .build();
        PdfTemplate guardado = repository.save(template);
        log.info("PdfTemplate creado: id={} tipo={}", LogSanitizer.safe(guardado.getId()), guardado.getTipo());
        return guardado;
    }

    @Transactional
    public PdfTemplate actualizar(String id, PdfTemplateUpdateDTO dto) {
        PdfTemplate existente = buscarOLanzar(id);
        validarSchemaJson(dto.schemaJson());
        existente.setNombre(dto.nombre());
        existente.setSchemaJson(dto.schemaJson());
        PdfTemplate guardado = repository.save(existente);
        log.info("PdfTemplate actualizado: id={}", LogSanitizer.safe(id));
        return guardado;
    }

    @Transactional
    public void eliminar(String id) {
        PdfTemplate existente = buscarOLanzar(id);
        if (existente.isActivo()) {
            throw new UnprocessableEntityException(
                    "No se puede eliminar un template activo. Activá otro primero o desactivá este.",
                    List.of("Template '" + existente.getNombre() + "' (tipo " + existente.getTipo()
                            + ") está activo."));
        }
        repository.delete(existente);
        log.info("PdfTemplate eliminado: id={}", LogSanitizer.safe(id));
    }

    /**
     * Activa un template: desactiva el resto del mismo tipo en la misma
     * transacción para garantizar el invariante "≤1 activo por tipo".
     *
     * <p>
     * Idempotente: si el target ya está activo, no hace nada.
     */
    @Transactional
    public PdfTemplate activar(String id) {
        PdfTemplate target = buscarOLanzar(id);
        if (target.isActivo()) {
            return target;
        }
        int desactivados = repository.desactivarOtrosDelTipo(target.getTipo(), id);
        target.setActivo(true);
        PdfTemplate guardado = repository.save(target);
        log.info(
                "PdfTemplate activado: id={} tipo={} (desactivados={})",
                LogSanitizer.safe(id),
                target.getTipo(),
                desactivados);
        return guardado;
    }

    /**
     * Verifica que el JSON parsea y tiene la forma mínima esperada por pdfme.
     * No es validación exhaustiva — confiamos en que el Designer produce JSON
     * válido. Esto solo evita errores groseros (string no JSON, top-level mal).
     */
    private void validarSchemaJson(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            throw new UnprocessableEntityException(
                    "schemaJson no puede estar vacío.", List.of());
        }
        try {
            JsonNode root = objectMapper.readTree(schemaJson);
            if (!root.isObject()) {
                throw new UnprocessableEntityException(
                        "schemaJson debe ser un objeto JSON.", List.of());
            }
            if (!root.has("basePdf") || !root.has("schemas")) {
                throw new UnprocessableEntityException(
                        "schemaJson debe contener las propiedades 'basePdf' y 'schemas'.",
                        List.of("Forma esperada: { \"basePdf\": \"...\", \"schemas\": [[...]] }"));
            }
            if (!root.get("schemas").isArray()) {
                throw new UnprocessableEntityException(
                        "schemaJson.schemas debe ser un array de páginas.", List.of());
            }
        } catch (JsonProcessingException e) {
            // Solo atrapamos errores de parseo de JSON. Otros runtimes (OOM,
            // StackOverflow por anidamiento extremo) suben como 500 — no
            // queremos enmascararlos como 422.
            throw new UnprocessableEntityException(
                    "schemaJson no es JSON válido: " + e.getOriginalMessage(), List.of());
        }
    }
}
