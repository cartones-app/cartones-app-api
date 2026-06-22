package com.eliasgonzalez.cartones.distribucion.mapper;

import com.eliasgonzalez.cartones.distribucion.service.dto.EtiquetaDTO;
import com.eliasgonzalez.cartones.distribucion.service.dto.ResumenDTO;
import com.eliasgonzalez.cartones.distribucion.controller.dto.VendedorSimuladoDTO;
import com.eliasgonzalez.cartones.vendedor.domain.ProcesoDistribucionVendedor;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

public class DistribucionMapper {

    public static List<EtiquetaDTO> toEtiquetaDTOs(
            List<VendedorSimuladoDTO> vendedorSimuladoDTOs,
            Map<Long, ProcesoDistribucionVendedor> registrosMap
    ) {
        DecimalFormatSymbols simbolos = new DecimalFormatSymbols(Locale.getDefault());
        simbolos.setGroupingSeparator('.');
        DecimalFormat df = new DecimalFormat("#,###", simbolos);

        List<EtiquetaDTO> etiquetaDTOs = new ArrayList<>();

        for (int i = 0; i < vendedorSimuladoDTOs.size(); i++) {
            VendedorSimuladoDTO simulado = vendedorSimuladoDTOs.get(i);
            ProcesoDistribucionVendedor registro = registrosMap.get(simulado.getId());

            if (registro == null) {
                throw new NullPointerException("El registro de distribución es null para id: " + simulado.getId());
            }

            String cantSenete = registro.getCantidadSenete() != null ? registro.getCantidadSenete().toString() : "0";
            String resSenete = registro.getResultadoSenete() != null ? registro.getResultadoSenete().toString() : "0";
            String cantTelebingo = registro.getCantidadTelebingo() != null ? registro.getCantidadTelebingo().toString() : "0";
            String resTelebingo = registro.getResultadoTelebingo() != null ? registro.getResultadoTelebingo().toString() : "0";
            String saldo = registro.getDeuda() != null ? df.format(registro.getDeuda()) : "0";

            etiquetaDTOs.add(EtiquetaDTO.builder()
                .numeroVendedor(i + 1)
                .nombre(simulado.getNombre())
                .saldo(saldo)
                .seneteRangos(simulado.getRangosSenete())
                .seneteCartones(cantSenete)
                .resultadoSenete(resSenete)
                .telebingoRangos(simulado.getRangosTelebingo())
                .telebingoCartones(cantTelebingo)
                .resultadoTelebingo(resTelebingo)
                .build()
            );
        }
        return etiquetaDTOs;
    }

    public static List<ResumenDTO> toResumenDTOs(
            List<VendedorSimuladoDTO> vendedorSimuladoDTOs,
            Map<Long, ProcesoDistribucionVendedor> registrosMap
    ) {
        List<ResumenDTO> resumenDTOs = new ArrayList<>();

        for (int i = 0; i < vendedorSimuladoDTOs.size(); i++) {
            VendedorSimuladoDTO simulado = vendedorSimuladoDTOs.get(i);
            ProcesoDistribucionVendedor registro = registrosMap.get(simulado.getId());

            if (registro == null) {
                throw new NullPointerException("El registro de distribución es null para id: " + simulado.getId());
            }

            int cantSenete = registro.getCantidadSenete() != null ? registro.getCantidadSenete() : 0;
            int cantTelebingo = registro.getCantidadTelebingo() != null ? registro.getCantidadTelebingo() : 0;

            Map<String, String> rangosSenete = extraerRangos(simulado.getRangosSenete());
            Map<String, String> rangosTelebingo = extraerRangos(simulado.getRangosTelebingo());

            resumenDTOs.add(ResumenDTO.builder()
                .numeroVendedor(i + 1)
                .nombre(simulado.getNombre())
                .seneteDelAl(rangosSenete)
                .cantidadSenete(cantSenete)
                .telebingoDelAl(rangosTelebingo)
                .cantidadTelebingo(cantTelebingo)
                .build()
            );
        }
        return resumenDTOs;
    }

    private static Map<String, String> extraerRangos(List<String> rangos) {
        Map<String, String> inicioFinRango = new HashMap<>();
        if (rangos == null) return inicioFinRango;
        for (String rango : rangos) {
            String[] x = rango.split("\\s*-\\s*");
            if (x.length >= 2) {
                inicioFinRango.put(x[0], x[1]);
            }
        }
        return inicioFinRango;
    }
}
