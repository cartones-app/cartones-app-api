package com.eliasgonzalez.cartones.distribucion.controller.dto;

import java.time.LocalDateTime;

public record ArchivosGeneradosDTO(
        String procesoId,
        LocalDateTime archivosGeneradosEn) {
}
