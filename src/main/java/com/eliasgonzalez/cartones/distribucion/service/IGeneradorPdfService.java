package com.eliasgonzalez.cartones.distribucion.service;

import com.eliasgonzalez.cartones.distribucion.controller.dto.VendedorSimuladoDTO;
import com.eliasgonzalez.cartones.distribucion.domain.ProcesoDistribucion;

import java.time.LocalDate;
import java.util.List;

public interface IGeneradorPdfService {

    void generarYPersistirArchivos(
            String procesoId,
            ProcesoDistribucion proceso,
            List<VendedorSimuladoDTO> config,
            LocalDate fechaSorteoSenete,
            LocalDate fechaSorteoTelebingo
    );
}
