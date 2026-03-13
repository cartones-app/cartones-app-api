package com.eliasgonzalez.cartones.common.util;

import org.apache.poi.ss.usermodel.*;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;

/**
 * Utilidades de lectura segura de celdas Excel con soporte para fórmulas.
 */
public class ExcelUtil {

    private ExcelUtil() {}

    public static boolean isRowEmpty(Row row, Integer colIdx, FormulaEvaluator evaluator) {
        if (row == null || colIdx == null) return true;
        Cell c = row.getCell(colIdx);
        if (c == null || c.getCellType() == CellType.BLANK) return true;

        try {
            CellValue cellValue = evaluator.evaluate(c);
            if (cellValue == null) return true;

            return switch (cellValue.getCellType()) {
                case STRING -> cellValue.getStringValue().trim().isBlank();
                case NUMERIC -> false;
                case BOOLEAN -> false;
                case ERROR -> true;
                default -> true;
            };
        } catch (Exception e) {
            return false;
        }
    }

    public static String getStringCell(Row row, Integer colIdx, FormulaEvaluator evaluator) {
        if (colIdx == null || row == null) return null;
        Cell c = row.getCell(colIdx);
        if (c == null || c.getCellType() == CellType.BLANK) return null;

        try {
            CellValue cellValue = evaluator.evaluate(c);
            if (cellValue == null || cellValue.getCellType() == CellType.BLANK) return null;

            return switch (cellValue.getCellType()) {
                case STRING -> {
                    String val = cellValue.getStringValue();
                    yield (val == null || val.trim().isEmpty()) ? null : val.trim();
                }
                case NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(c)) {
                        yield new SimpleDateFormat("yyyy/MM/dd").format(c.getDateCellValue());
                    }
                    yield BigDecimal.valueOf(cellValue.getNumberValue())
                            .stripTrailingZeros()
                            .toPlainString();
                }
                case BOOLEAN -> String.valueOf(cellValue.getBooleanValue());
                case ERROR -> "ERROR_EXCEL_" + FormulaError.forInt(cellValue.getErrorValue()).getString();
                default -> null;
            };
        } catch (Exception e) {
            return "ERROR_PROCESAMIENTO_CELDA";
        }
    }

    public static Integer getIntCell(Row row, Integer colIdx, FormulaEvaluator evaluator) {
        if (colIdx == null || row == null) return null;
        Cell c = row.getCell(colIdx);
        if (c == null) return null;

        try {
            CellValue cellValue = evaluator.evaluate(c);
            if (cellValue == null || cellValue.getCellType() == CellType.BLANK) return null;

            if (cellValue.getCellType() == CellType.NUMERIC) {
                return (int) Math.round(cellValue.getNumberValue());
            }
            if (cellValue.getCellType() == CellType.STRING) {
                return (int) Double.parseDouble(cellValue.getStringValue());
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}
