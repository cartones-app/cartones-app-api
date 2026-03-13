package com.eliasgonzalez.cartones.vendedor.controller;

import com.eliasgonzalez.cartones.common.exception.ExcelProcessingException;
import com.eliasgonzalez.cartones.vendedor.dto.FilasIgnoradasDTO;
import com.eliasgonzalez.cartones.vendedor.dto.VendedorResponseDTO;
import com.eliasgonzalez.cartones.vendedor.interfaces.IVendedorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/vendedores")
@Slf4j
@CrossOrigin(origins = "${app.cors.origins}")
public class VendedorController {

    private final IVendedorService vendedorService;

    @GetMapping("/{procesoId}")
    public ResponseEntity<List<VendedorResponseDTO>> listarVendedoresValidos (
            @PathVariable (name = "procesoId") String procesoIdRecibido
    ){
        log.debug("GET /api/vendedores/{}", procesoIdRecibido);
        return ResponseEntity.ok(vendedorService.listarVendedoresValidos(procesoIdRecibido));
    }

    @PostMapping(value = "/carga", consumes = "multipart/form-data")
    public ResponseEntity<FilasIgnoradasDTO> cargarVendedoresDesdeExcel(
            @RequestParam("file") MultipartFile file) {

        log.debug("POST /api/vendedores/carga - archivo: {}", file != null ? file.getOriginalFilename() : "null");
        // PRE-VALIDACIÓN: Antes de tocar el InputStream
        if (file == null || file.isEmpty()) {
            log.error("El archivo recibido es nulo o está vacío.");
            throw new ExcelProcessingException("El archivo recibido es nulo o está vacío.", List.of());
        }

        String procesoIdCreado = vendedorService.iniciarProceso();
        FilasIgnoradasDTO filasIgnoradas = vendedorService.procesarExcel(file, procesoIdCreado);
        return ResponseEntity.ok(filasIgnoradas);
    }


}
