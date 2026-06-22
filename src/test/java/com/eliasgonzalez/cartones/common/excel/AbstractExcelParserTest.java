package com.eliasgonzalez.cartones.common.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.eliasgonzalez.cartones.common.exception.ExcelProcessingException;

class AbstractExcelParserTest {

    /** Parser concreto mínimo que solo expone los métodos protected para test. */
    private static class TestParser extends AbstractExcelParser {
        public Sheet obtener(Workbook wb, String hoja) {
            return obtenerHoja(wb, hoja);
        }

        public Map<String, Integer> indiceCols(Sheet s) {
            return construirIndiceColumnas(s);
        }

        public void validar(Map<String, Integer> idx, List<String> req) {
            validarEncabezados(idx, req);
        }
    }

    private final TestParser parser = new TestParser();

    @Test
    void obtenerHoja_lanzaSiNoExisteLaHoja() {
        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet("Otra");

            assertThatThrownBy(() -> parser.obtener(wb, "Datos"))
                    .isInstanceOf(ExcelProcessingException.class)
                    .hasMessageContaining("'Datos'");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void obtenerHoja_devuelveSheetSiExiste() {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet creada = wb.createSheet("Datos");

            Sheet result = parser.obtener(wb, "Datos");

            assertThat(result).isSameAs(creada);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void construirIndiceColumnas_lanzaSiNoHayFilaDeEncabezados() {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Datos");
            // Sin row 0.

            assertThatThrownBy(() -> parser.indiceCols(sheet))
                    .isInstanceOf(ExcelProcessingException.class)
                    .hasMessageContaining("encabezados");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void construirIndiceColumnas_normalizaNombresYMapeaAIndices() {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Datos");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("VENDEDOR");
            header.createCell(1).setCellValue("Saldo");
            header.createCell(2).setCellValue("  CANT_SENETE  ");

            Map<String, Integer> idx = parser.indiceCols(sheet);

            // Los nombres se normalizan (lower, trim) por TextoUtil — solo verificamos
            // que los 3 estén mapeados con índices correctos.
            assertThat(idx).hasSize(3);
            assertThat(idx.values()).containsExactlyInAnyOrder(0, 1, 2);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void construirIndiceColumnas_ignoraCeldasNoStringYVacias() {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Datos");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Nombre");
            header.createCell(1).setCellValue(42); // numérico → ignorado
            header.createCell(2).setCellValue(""); // vacío → ignorado
            header.createCell(3).setCellValue("Total");

            Map<String, Integer> idx = parser.indiceCols(sheet);

            assertThat(idx.values()).containsExactlyInAnyOrder(0, 3);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void validarEncabezados_pasaSiTodosLosRequeridosEstanPresentes() {
        Map<String, Integer> idx = Map.of("nombre", 0, "saldo", 1, "total", 2);

        // No debe lanzar.
        parser.validar(idx, List.of("Nombre", "Saldo"));
    }

    @Test
    void validarEncabezados_lanzaConListaDeFaltantes() {
        Map<String, Integer> idx = Map.of("nombre", 0);

        assertThatThrownBy(() -> parser.validar(idx, List.of("Nombre", "Saldo", "Total")))
                .isInstanceOf(ExcelProcessingException.class)
                .hasMessageContaining("Faltan columnas")
                .hasMessageContaining("Saldo")
                .hasMessageContaining("Total");
    }

    @Test
    void validarEncabezados_listaRequeridosVaciaNoLanza() {
        Map<String, Integer> idx = Map.of();

        parser.validar(idx, List.of()); // no debe lanzar
    }
}
