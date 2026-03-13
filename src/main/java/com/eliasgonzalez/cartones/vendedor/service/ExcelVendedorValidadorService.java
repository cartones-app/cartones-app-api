package com.eliasgonzalez.cartones.vendedor.service;

import com.eliasgonzalez.cartones.vendedor.service.dto.VendedorExcelDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExcelVendedorValidadorService {

    /**
     * Valida el DTO de Excel contra las reglas de negocio (no nulo, formato).
     * Devuelve una lista de errores que incluye el número de fila.
     */
    public List<String> validate(VendedorExcelDTO dto) {
        List<String> rowErrors = new ArrayList<>();
        int filaActual = dto.getFilaActual();

        if (dto.getNombre() == null || dto.getNombre().isBlank()) {
            rowErrors.add(String.format("Fila %d: El campo NOMBRE del vendedor no puede estar vacío.", filaActual));
        }

        String deudaStr = dto.getDeudaStr();
        if (deudaStr != null && !deudaStr.isBlank()) {
            try {
                new BigDecimal(deudaStr.trim());
            } catch (NumberFormatException e) {
                rowErrors.add(String.format("Fila %d: El campo SALDO ('%s') no es un número válido.", filaActual, deudaStr));
            }
        }

        return rowErrors;
    }
}
