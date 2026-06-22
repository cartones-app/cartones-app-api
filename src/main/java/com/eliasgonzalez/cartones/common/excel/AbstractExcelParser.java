package com.eliasgonzalez.cartones.common.excel;

import com.eliasgonzalez.cartones.common.exception.ExcelProcessingException;
import com.eliasgonzalez.cartones.common.util.TextoUtil;
import org.apache.poi.ss.usermodel.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Clase base con utilidades comunes para analizar archivos Excel con Apache POI.
 * Centraliza la obtención de hojas, construcción del índice de columnas,
 * validación de encabezados y creación del evaluador de fórmulas.
 *
 * Cada parser concreto extiende esta clase y le agrega su lógica de dominio.
 */
public abstract class AbstractExcelParser {

    /**
     * Busca una hoja por nombre. Lanza {@link ExcelProcessingException} si no existe.
     */
    protected Sheet obtenerHoja(Workbook wb, String nombreHoja) {
        int idx = wb.getSheetIndex(nombreHoja);
        if (idx < 0) {
            throw new ExcelProcessingException(
                "La hoja '" + nombreHoja + "' no fue encontrada en el Excel.", List.of()
            );
        }
        return wb.getSheetAt(idx);
    }

    /**
     * Lee la fila 0 del sheet y construye un mapa { nombre_normalizado -> índice_columna }.
     * Solo considera celdas de tipo STRING con contenido no vacío.
     */
    protected Map<String, Integer> construirIndiceColumnas(Sheet sheet) {
        Row header = sheet.getRow(0);
        if (header == null) {
            throw new ExcelProcessingException("El Excel no tiene fila de encabezados.", List.of());
        }
        Map<String, Integer> idx = new HashMap<>();
        for (Cell c : header) {
            if (c.getCellType() == CellType.STRING) {
                String nombre = c.getStringCellValue();
                if (nombre != null && !nombre.isBlank()) {
                    idx.put(TextoUtil.normalize(nombre), c.getColumnIndex());
                }
            }
        }
        return idx;
    }

    /**
     * Verifica que todas las columnas requeridas existan en el índice.
     * Lanza {@link ExcelProcessingException} con la lista de columnas faltantes si alguna no se encuentra.
     */
    protected void validarEncabezados(Map<String, Integer> idx, List<String> requeridos) {
        List<String> faltantes = new ArrayList<>();
        for (String col : requeridos) {
            if (!idx.containsKey(TextoUtil.normalize(col))) faltantes.add(col);
        }
        if (!faltantes.isEmpty()) {
            throw new ExcelProcessingException("Faltan columnas requeridas en el Excel: " + faltantes, List.of());
        }
    }

    /**
     * Crea y prepara el evaluador de fórmulas para el workbook dado.
     * Limpia la caché y fuerza la evaluación de todas las celdas.
     */
    protected FormulaEvaluator crearEvaluador(Workbook wb) {
        FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
        evaluator.clearAllCachedResultValues();
        evaluator.evaluateAll();
        return evaluator;
    }
}
