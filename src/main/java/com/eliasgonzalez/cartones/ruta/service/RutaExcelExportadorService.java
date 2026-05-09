package com.eliasgonzalez.cartones.ruta.service;

import com.eliasgonzalez.cartones.common.excel.AbstractExcelParser;
import com.eliasgonzalez.cartones.common.exception.ExcelProcessingException;
import com.eliasgonzalez.cartones.common.exception.ResourceNotFoundException;
import com.eliasgonzalez.cartones.common.util.TextoUtil;
import com.eliasgonzalez.cartones.ruta.controller.dto.RegistroRutaDTO;
import com.eliasgonzalez.cartones.ruta.domain.SesionRuta;
import com.eliasgonzalez.cartones.ruta.domain.SesionRutaRegistro;
import com.eliasgonzalez.cartones.ruta.domain.enums.EstadoSesionEnum;
import com.eliasgonzalez.cartones.ruta.domain.enums.RutaColumnaEnum;
import com.eliasgonzalez.cartones.ruta.repository.SesionRutaRegistroRepository;
import com.eliasgonzalez.cartones.ruta.repository.SesionRutaRepository;
import com.eliasgonzalez.cartones.vendedor.domain.Vendedor;
import com.eliasgonzalez.cartones.vendedor.repository.VendedorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Escribe los datos completados por el distribuidor en el Excel de ruta,
 * persiste los registros en sesion_ruta_registro y marca la sesión como COMPLETADA.
 *
 * Regla fundamental: las celdas con fórmula NO se modifican.
 * Solo se escriben las 8 columnas de entrada:
 *   SENETE_TOTAL_ENVIADO, TELEBINGO_TOTAL_ENVIADO, REF_SENETE, REF_TELB,
 *   DEV_SEN, DEV_TELB, PAGO1, PAGO2
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RutaExcelExportadorService extends AbstractExcelParser {

    private final SesionRutaRepository sesionRutaRepo;
    private final SesionRutaRegistroRepository registroRepo;
    private final VendedorRepository vendedorRepo;

    /**
     * @return bytes del Excel modificado para descarga directa.
     */
    @Transactional
    public byte[] exportar(String sesionId, List<RegistroRutaDTO> registros) {
        SesionRuta sesion = sesionRutaRepo.findBySesionId(sesionId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Sesión de ruta con ID " + sesionId + " no encontrada.", List.of()
            ));

        byte[] excelModificado;
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(sesion.getArchivoExcel()))) {
            Sheet sheet = obtenerHoja(wb, RutaColumnaEnum.HOJA.getValor());
            Map<String, Integer> idx = construirIndiceColumnas(sheet);

            List<SesionRutaRegistro> registrosAGuardar = new ArrayList<>();

            int ultimaFila = sheet.getLastRowNum();
            List<String> erroresFila = new ArrayList<>();
            for (RegistroRutaDTO dto : registros) {
                int fila = dto.getNumeroFila();
                if (fila < 0 || fila > ultimaFila) {
                    erroresFila.add(String.format(
                        "numeroFila=%d fuera de rango [0, %d] (vendedor: '%s')",
                        fila, ultimaFila, dto.getNombre()));
                    continue;
                }
                Row row = sheet.getRow(fila);
                if (row == null) {
                    erroresFila.add(String.format(
                        "Fila %d vacía o ausente en el Excel (vendedor: '%s')",
                        fila, dto.getNombre()));
                    continue;
                }

                escribirColumnasEntrada(row, idx, dto);
                registrosAGuardar.add(construirRegistro(sesion, dto));
            }

            if (!erroresFila.isEmpty()) {
                log.warn("Sesión {}: {} registro(s) con fila inválida.", sesionId, erroresFila.size());
                throw new ExcelProcessingException(
                    "Hay registros con número de fila inválido para esta sesión.", erroresFila);
            }

            registroRepo.saveAll(registrosAGuardar);

            sesion.setRegistrosCompletados(registrosAGuardar.size());
            sesion.setEstado(EstadoSesionEnum.COMPLETADA.getValor());
            sesionRutaRepo.save(sesion);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            excelModificado = baos.toByteArray();

        } catch (ExcelProcessingException | ResourceNotFoundException e) {
            throw e;
        } catch (IOException e) {
            throw new ExcelProcessingException("Error al escribir el Excel de exportación: " + e.getMessage(), List.of());
        } catch (Exception e) {
            throw new ExcelProcessingException("Error inesperado al exportar: " + e.getMessage(), List.of());
        }

        log.info("Sesión {}: exportación completada. {} registros persistidos.", sesionId, registros.size());
        return excelModificado;
    }

    // ---------------------------------------------------------------------------
    // Escritura de celdas
    // ---------------------------------------------------------------------------

    /**
     * Escribe únicamente las 8 columnas de entrada del Excel de ruta.
     * Las celdas con fórmula se omiten — Excel las recalcula al abrir el archivo.
     */
    private void escribirColumnasEntrada(Row row, Map<String, Integer> idx, RegistroRutaDTO dto) {
        escribirEntero(row, idx.get(TextoUtil.normalize(RutaColumnaEnum.SENETE_TOTAL_ENVIADO.getValor())),
            dto.getSeneteTotalEnviado());
        escribirEntero(row, idx.get(TextoUtil.normalize(RutaColumnaEnum.TELEBINGO_TOTAL_ENVIADO.getValor())),
            dto.getTelebingoTotalEnviado());
        escribirEntero(row, idx.get(TextoUtil.normalize(RutaColumnaEnum.REF_SENETE.getValor())),
            dto.getRefSenete());
        escribirEntero(row, idx.get(TextoUtil.normalize(RutaColumnaEnum.REF_TELB.getValor())),
            dto.getRefTelb());
        escribirEntero(row, idx.get(TextoUtil.normalize(RutaColumnaEnum.DEV_SEN.getValor())),
            dto.getDevSen());
        escribirEntero(row, idx.get(TextoUtil.normalize(RutaColumnaEnum.DEV_TELB.getValor())),
            dto.getDevTelb());
        escribirDecimal(row, idx.get(TextoUtil.normalize(RutaColumnaEnum.PAGO1.getValor())),
            dto.getPago1());
        escribirDecimal(row, idx.get(TextoUtil.normalize(RutaColumnaEnum.PAGO2.getValor())),
            dto.getPago2());
    }

    private void escribirEntero(Row row, Integer colIdx, Integer valor) {
        if (colIdx == null || valor == null) return;
        Cell cell = obtenerCeldaNoFormula(row, colIdx);
        if (cell != null) cell.setCellValue(valor.doubleValue());
    }

    private void escribirDecimal(Row row, Integer colIdx, BigDecimal valor) {
        if (colIdx == null || valor == null) return;
        Cell cell = obtenerCeldaNoFormula(row, colIdx);
        if (cell != null) cell.setCellValue(valor.doubleValue());
    }

    /**
     * Devuelve la celda si NO contiene fórmula. Si tiene fórmula, devuelve null y loguea un warning.
     * Si la celda no existe, la crea.
     */
    private Cell obtenerCeldaNoFormula(Row row, int colIdx) {
        Cell cell = row.getCell(colIdx);
        if (cell == null) {
            return row.createCell(colIdx, CellType.NUMERIC);
        }
        if (cell.getCellType() == CellType.FORMULA) {
            log.warn("Fila {}, columna {}: celda con fórmula omitida en la exportación.",
                row.getRowNum() + 1, colIdx);
            return null;
        }
        return cell;
    }

    // ---------------------------------------------------------------------------
    // Construcción de entidad
    // ---------------------------------------------------------------------------

    private SesionRutaRegistro construirRegistro(SesionRuta sesion, RegistroRutaDTO dto) {
        Vendedor vendedor = vendedorRepo.findById(dto.getVendedorId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Vendedor con ID " + dto.getVendedorId() + " no encontrado.", List.of()
            ));

        return SesionRutaRegistro.builder()
            .sesionRuta(sesion)
            .vendedor(vendedor)
            .fecha(parsearFecha(dto.getFecha()))
            .seneteTotalEnviado(dto.getSeneteTotalEnviado())
            .telebingoTotalEnviado(dto.getTelebingoTotalEnviado())
            .refSenete(dto.getRefSenete())
            .refTelb(dto.getRefTelb())
            .devSen(dto.getDevSen())
            .devTelb(dto.getDevTelb())
            .pago1(dto.getPago1())
            .pago2(dto.getPago2())
            .nota(dto.getNota())
            .completado(true)
            .build();
    }

    /**
     * Parsea la fecha del Excel. Intenta los formatos yyyy/MM/dd y dd/MM/yyyy.
     */
    private LocalDate parsearFecha(String fechaStr) {
        if (fechaStr == null || fechaStr.isBlank()) return null;
        String f = fechaStr.trim();
        for (String pattern : List.of("yyyy/MM/dd", "dd/MM/yyyy")) {
            try {
                return LocalDate.parse(f, DateTimeFormatter.ofPattern(pattern));
            } catch (DateTimeParseException ignored) {
                // intenta el siguiente
            }
        }
        log.warn("No se pudo parsear la fecha '{}'. Se guardará como null.", fechaStr);
        return null;
    }
}
