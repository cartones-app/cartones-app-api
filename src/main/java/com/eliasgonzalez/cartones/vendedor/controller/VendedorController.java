package com.eliasgonzalez.cartones.vendedor.controller;

import com.eliasgonzalez.cartones.common.util.MultipartFileValidator;
import com.eliasgonzalez.cartones.vendedor.controller.dto.CargaVendedoresResponseDTO;
import com.eliasgonzalez.cartones.vendedor.controller.dto.VendedorResponseDTO;
import com.eliasgonzalez.cartones.vendedor.service.IVendedorService;
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
    public ResponseEntity<CargaVendedoresResponseDTO> cargarVendedoresDesdeExcel(
            @RequestParam("file") MultipartFile file) {

        log.debug("POST /api/vendedores/carga - archivo: {}", file != null ? file.getOriginalFilename() : "null");
        MultipartFileValidator.validarXlsx(file);

        String procesoIdCreado = vendedorService.iniciarProceso();
        CargaVendedoresResponseDTO filasIgnoradas = vendedorService.procesarExcel(file, procesoIdCreado);
        return ResponseEntity.ok(filasIgnoradas);
    }


}
