package com.eliasgonzalez.cartones.pdftemplate.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eliasgonzalez.cartones.pdftemplate.controller.dto.PdfTemplateCreateDTO;
import com.eliasgonzalez.cartones.pdftemplate.controller.dto.PdfTemplateDetalleDTO;
import com.eliasgonzalez.cartones.pdftemplate.controller.dto.PdfTemplateResumenDTO;
import com.eliasgonzalez.cartones.pdftemplate.controller.dto.PdfTemplateUpdateDTO;
import com.eliasgonzalez.cartones.pdftemplate.mapper.PdfTemplateMapper;
import com.eliasgonzalez.cartones.pdftemplate.service.PdfTemplateService;

import lombok.RequiredArgsConstructor;

/**
 * Endpoints admin para CRUD de templates de PDF.
 *
 * <p>
 * Doble candado: el path {@code /api/admin/**} ya pide rol ADMIN en
 * SecurityConfig, y {@code @PreAuthorize} a nivel clase es defensa redundante.
 *
 * <p>
 * El logging de auditoría vive en el service (creado / actualizado / activado
 * / eliminado), no acá — los controllers solo orquestan.
 */
@RestController
@RequestMapping("/api/admin/pdf-templates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPdfTemplateController {

    private final PdfTemplateService service;

    @GetMapping
    public ResponseEntity<List<PdfTemplateResumenDTO>> listar() {
        return ResponseEntity.ok(service.listar().stream().map(PdfTemplateMapper::toResumen).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PdfTemplateDetalleDTO> obtener(@PathVariable String id) {
        return ResponseEntity.ok(PdfTemplateMapper.toDetalle(service.obtener(id)));
    }

    @PostMapping
    public ResponseEntity<PdfTemplateDetalleDTO> crear(@Valid @RequestBody PdfTemplateCreateDTO dto) {
        return ResponseEntity.ok(PdfTemplateMapper.toDetalle(service.crear(dto)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PdfTemplateDetalleDTO> actualizar(
            @PathVariable String id, @Valid @RequestBody PdfTemplateUpdateDTO dto) {
        return ResponseEntity.ok(PdfTemplateMapper.toDetalle(service.actualizar(id, dto)));
    }

    @PostMapping("/{id}/activar")
    public ResponseEntity<PdfTemplateDetalleDTO> activar(@PathVariable String id) {
        return ResponseEntity.ok(PdfTemplateMapper.toDetalle(service.activar(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
