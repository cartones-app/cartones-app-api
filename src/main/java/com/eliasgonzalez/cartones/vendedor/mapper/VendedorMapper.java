package com.eliasgonzalez.cartones.vendedor.mapper;

import com.eliasgonzalez.cartones.vendedor.dto.VendedorResponseDTO;
import com.eliasgonzalez.cartones.vendedor.entity.ProcesoDistribucionVendedor;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class VendedorMapper {

    private VendedorMapper() {}

    public static VendedorResponseDTO toVendedorResponseDTO(ProcesoDistribucionVendedor registro) {
        if (registro == null) {
            return null;
        }

        return VendedorResponseDTO.builder()
            .id(registro.getId())
            .nombre(registro.getVendedor().getNombre())
            .deuda(registro.getDeuda())
            .cantidadSenete(registro.getCantidadSenete())
            .resultadoSenete(registro.getResultadoSenete())
            .cantidadTelebingo(registro.getCantidadTelebingo())
            .resultadoTelebingo(registro.getResultadoTelebingo())
            .build();
    }

    public static List<VendedorResponseDTO> toVendedorResponseDTOs(List<ProcesoDistribucionVendedor> registros) {
        if (registros == null || registros.isEmpty()) {
            return Collections.emptyList();
        }
        return registros.stream()
            .map(VendedorMapper::toVendedorResponseDTO)
            .collect(Collectors.toList());
    }
}
