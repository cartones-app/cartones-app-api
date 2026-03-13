package com.eliasgonzalez.cartones.ruta.controller;

import com.eliasgonzalez.cartones.ruta.controller.dto.CargaRutaResponseDTO;
import com.eliasgonzalez.cartones.ruta.controller.dto.ExportarRutaRequestDTO;
import com.eliasgonzalez.cartones.ruta.controller.dto.FiltroFechaRequestDTO;
import com.eliasgonzalez.cartones.ruta.controller.dto.RegistroRutaDTO;
import com.eliasgonzalez.cartones.ruta.service.RutaExcelExportadorService;
import com.eliasgonzalez.cartones.ruta.service.RutaExcelLectorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Endpoints del flujo de ruta para el distribuidor.
 * Paso 1: POST /api/ruta/carga       → sube el Excel, devuelve sesionId + fechas disponibles
 * Paso 2: POST /api/ruta/{sesionId}/registros → filtra por fecha(s), devuelve los vendedores del recorrido
 */
@RestController
@RequestMapping("/api/ruta")
@RequiredArgsConstructor
public class RutaController {

    private final RutaExcelLectorService rutaExcelLectorService;
    private final RutaExcelExportadorService rutaExcelExportadorService;

    /**
     * Recibe el Excel de ruta, lo persiste como BLOB y devuelve las fechas únicas disponibles.
     * El frontend usa el sesionId para todos los pasos siguientes.
     */
    @PostMapping("/carga")
    public ResponseEntity<CargaRutaResponseDTO> cargarExcel(
            @RequestParam("file") MultipartFile file) {
        CargaRutaResponseDTO response = rutaExcelLectorService.cargarExcel(file);
        return ResponseEntity.ok(response);
    }

    /**
     * Filtra el Excel almacenado en sesión por las fechas indicadas.
     * Aplica exclusiones (BD + color rojo) y devuelve los registros para el recorrido.
     */
    @PostMapping("/{sesionId}/registros")
    public ResponseEntity<List<RegistroRutaDTO>> obtenerRegistros(
            @PathVariable String sesionId,
            @Valid @RequestBody FiltroFechaRequestDTO request) {
        List<RegistroRutaDTO> registros = rutaExcelLectorService.filtrarPorFechas(sesionId, request.getFechas());
        return ResponseEntity.ok(registros);
    }

    /**
     * Recibe los registros completados, escribe los valores en el Excel original
     * (sin tocar fórmulas), persiste en sesion_ruta_registro y devuelve el Excel para descarga.
     */
    @PostMapping("/{sesionId}/exportar")
    public ResponseEntity<byte[]> exportar(
            @PathVariable String sesionId,
            @Valid @RequestBody ExportarRutaRequestDTO request) {
        byte[] excelBytes = rutaExcelExportadorService.exportar(sesionId, request.getRegistros());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(
            ContentDisposition.attachment().filename("ruta_" + sesionId + ".xlsx").build());

        return ResponseEntity.ok().headers(headers).body(excelBytes);
    }
}
