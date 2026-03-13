package com.eliasgonzalez.cartones.distribucion.controller.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SimulacionRequestDTO {

    // Rangos disponibles
    private List<RangoCortadoDTO> poolSenete;
    private List<RangoCortadoDTO> poolTelebingo;

    // Datos numéricos
    private Integer inicioSeneteGral;
    private Integer inicioTelebingoGral;

    // Lista de vendedores
    @Valid
    @NotNull
    private List<VendedorInputDTO> vendedores;

    // Fechas
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate fechaSorteoSenete = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.SUNDAY));

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate fechaSorteoTelebingo = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.SUNDAY));

    // Bandera de aleatoriedad
    private boolean mezclar = true;


    // --- SETTERS MANUALES (Lombok NO sobrescribe estos) ---

    @JsonProperty("inicioSenete")
    public void setInicioSeneteGral(Integer inicioSeneteGral) {
        this.inicioSeneteGral = (inicioSeneteGral == null) ? 0 : inicioSeneteGral;
    }

    @JsonProperty("inicioTelebingo")
    public void setInicioTelebingoGral(Integer inicioTelebingoGral) {
        this.inicioTelebingoGral = (inicioTelebingoGral == null) ? 0 : inicioTelebingoGral;
    }

    @JsonProperty("fechaSorteoSenete")
    public void setFechaSorteoSenete(LocalDate fechaSorteoSenete) {
        this.fechaSorteoSenete = (fechaSorteoSenete == null)
                ? LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
                : fechaSorteoSenete;
    }

    @JsonProperty("fechaSorteoTelebingo")
    public void setFechaSorteoTelebingo(LocalDate fechaSorteoTelebingo) {
        this.fechaSorteoTelebingo = (fechaSorteoTelebingo == null)
                ? LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
                : fechaSorteoTelebingo;
    }

    @Override
    public String toString() {
        return "SimulacionRequestDTO{" +
                "poolSenete=" + poolSenete +
                ", poolTelebingo=" + poolTelebingo +
                ", inicioSeneteGral=" + inicioSeneteGral +
                ", inicioTelebingoGral=" + inicioTelebingoGral +
                ", vendedores=" + vendedores +
                ", fechaSorteoSenete=" + fechaSorteoSenete +
                ", fechaSorteoTelebingo=" + fechaSorteoTelebingo +
                ", mezclar=" + mezclar +
                '}';
    }
}