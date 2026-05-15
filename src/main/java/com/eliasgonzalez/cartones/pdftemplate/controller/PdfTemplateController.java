package com.eliasgonzalez.cartones.pdftemplate.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.eliasgonzalez.cartones.pdftemplate.controller.dto.PdfTemplateActiveDTO;
import com.eliasgonzalez.cartones.pdftemplate.domain.enums.PdfTemplateTipo;
import com.eliasgonzalez.cartones.pdftemplate.mapper.PdfTemplateMapper;
import com.eliasgonzalez.cartones.pdftemplate.service.PdfTemplateService;

import lombok.RequiredArgsConstructor;

/**
 * Endpoint público (autenticado, no admin) para que el cliente obtenga el
 * template activo y arme el PDF con pdfme.
 *
 * <p>
 * Cae bajo {@code /api/**} que ya pide {@code hasAnyRole(ADMIN,DISTRIBUIDOR)}
 * en {@code SecurityConfig}. No exponemos templates a usuarios anónimos.
 */
@RestController
@RequestMapping("/api/pdf-templates")
@RequiredArgsConstructor
public class PdfTemplateController {

    private final PdfTemplateService service;

    @GetMapping("/active")
    public ResponseEntity<PdfTemplateActiveDTO> obtenerActivo(@RequestParam PdfTemplateTipo tipo) {
        return ResponseEntity.ok(PdfTemplateMapper.toActive(service.obtenerActivo(tipo)));
    }
}
