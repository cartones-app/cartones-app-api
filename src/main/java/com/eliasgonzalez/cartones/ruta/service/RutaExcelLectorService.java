package com.eliasgonzalez.cartones.ruta.service;

import com.eliasgonzalez.cartones.common.excel.AbstractExcelParser;
import com.eliasgonzalez.cartones.common.exception.ExcelProcessingException;
import com.eliasgonzalez.cartones.common.exception.ResourceNotFoundException;
import com.eliasgonzalez.cartones.common.util.ExcelUtil;
import com.eliasgonzalez.cartones.common.util.TextoUtil;
import com.eliasgonzalez.cartones.ruta.controller.dto.CargaRutaResponseDTO;
import com.eliasgonzalez.cartones.ruta.controller.dto.RegistroRutaDTO;
import com.eliasgonzalez.cartones.ruta.domain.SesionRuta;
import com.eliasgonzalez.cartones.ruta.domain.enums.RutaColumnaEnum;
import com.eliasgonzalez.cartones.ruta.repository.ExclusionRutaRepository;
import com.eliasgonzalez.cartones.ruta.repository.SesionRutaRepository;
import com.eliasgonzalez.cartones.vendedor.domain.Vendedor;
import com.eliasgonzalez.cartones.vendedor.repository.VendedorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class RutaExcelLectorService extends AbstractExcelParser {

    private static final List<String> COLUMNAS_REQUERIDAS = List.of(
        RutaColumnaEnum.FECHA.getValor(),
        RutaColumnaEnum.VENDEDOR.getValor()
    );

    private final SesionRutaRepository sesionRutaRepo;
    private final ExclusionRutaRepository exclusionRutaRepo;
    private final VendedorRepository vendedorRepo;

    /**
     * Recibe el Excel, extrae las fechas únicas disponibles y crea la sesión de ruta.
     * El Excel se guarda como BLOB en la sesión para usarse luego en el filtrado y la exportación.
     */
    @Transactional
    public CargaRutaResponseDTO cargarExcel(MultipartFile file) {
        byte[] excelBytes;
        try {
            excelBytes = file.getBytes();
        } catch (IOException e) {
            throw new ExcelProcessingException("No se pudo leer el archivo Excel: " + e.getMessage(), List.of());
        }

        List<String> fechasDisponibles;
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(excelBytes))) {
            Sheet sheet = obtenerHoja(wb, RutaColumnaEnum.HOJA.getValor());
            Map<String, Integer> idx = construirIndiceColumnas(sheet);
            validarEncabezados(idx, COLUMNAS_REQUERIDAS);
            fechasDisponibles = extraerFechasUnicas(sheet, idx, crearEvaluador(wb));
        } catch (ExcelProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new ExcelProcessingException("Error al procesar el Excel: " + e.getMessage(), List.of());
        }

        String sesionId = UUID.randomUUID().toString();
        SesionRuta sesion = SesionRuta.builder()
            .sesionId(sesionId)
            .fechaFiltro("") // se actualiza cuando el usuario elige la fecha
            .archivoExcel(excelBytes)
            .build();
        sesionRutaRepo.save(sesion);

        log.info("Sesión de ruta creada: {}. Fechas disponibles: {}", sesionId, fechasDisponibles);
        return CargaRutaResponseDTO.builder()
            .sesionId(sesionId)
            .fechasDisponibles(fechasDisponibles)
            .build();
    }

    /**
     * Filtra el Excel por las fechas seleccionadas, aplica exclusiones y devuelve los registros.
     * Orden de exclusión:
     *   1. Filas desde la primera con "TOTAL" en VENDEDOR en adelante (límite estructural fijo)
     *   2. Lista de exclusiones en BD (gestionable por ADMIN)
     *   3. Celdas VENDEDOR con color rojo en el Excel
     * Deduplicación: primera ocurrencia por nombre de vendedor para cada fecha.
     */
    @Transactional
    public List<RegistroRutaDTO> filtrarPorFechas(String sesionId, List<String> fechasSeleccionadas) {
        SesionRuta sesion = sesionRutaRepo.findBySesionId(sesionId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Sesión de ruta con ID " + sesionId + " no encontrada.", List.of()
            ));

        sesion.setFechaFiltro(String.join(",", fechasSeleccionadas));
        sesionRutaRepo.save(sesion);

        Set<String> nombresExcluidos = new HashSet<>();
        exclusionRutaRepo.findByActivoTrue()
            .forEach(e -> nombresExcluidos.add(TextoUtil.normalize(e.getNombre())));

        List<RegistroRutaDTO> registros;
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(sesion.getArchivoExcel()))) {
            FormulaEvaluator evaluator = crearEvaluador(wb);
            Sheet sheet = obtenerHoja(wb, RutaColumnaEnum.HOJA.getValor());
            Map<String, Integer> idx = construirIndiceColumnas(sheet);
            validarEncabezados(idx, COLUMNAS_REQUERIDAS);

            registros = leerRegistrosFiltrados(sheet, idx, evaluator, fechasSeleccionadas, nombresExcluidos);
        } catch (ExcelProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new ExcelProcessingException("Error al filtrar el Excel: " + e.getMessage(), List.of());
        }

        // Upsert de vendedores en la tabla maestra
        registros.forEach(r -> {
            Vendedor vendedor = vendedorRepo.findByNombreIgnoreCase(r.getNombre())
                .orElseGet(() -> vendedorRepo.save(Vendedor.builder().nombre(r.getNombre()).build()));
            r.setVendedorId(vendedor.getId());
        });

        sesion.setTotalRegistros(registros.size());
        sesionRutaRepo.save(sesion);

        log.info("Sesión {}: {} registros filtrados para fechas {}", sesionId, registros.size(), fechasSeleccionadas);
        return registros;
    }

    // ---------------------------------------------------------------------------
    // Métodos privados de parseo
    // ---------------------------------------------------------------------------

    /**
     * Extrae las fechas únicas de la columna FECHA, respetando el límite estructural (TOTAL).
     * Mantiene el orden de aparición.
     */
    private List<String> extraerFechasUnicas(Sheet sheet, Map<String, Integer> idx, FormulaEvaluator evaluator) {
        Integer fechaIdx = idx.get(TextoUtil.normalize(RutaColumnaEnum.FECHA.getValor()));
        Integer vendedorIdx = idx.get(TextoUtil.normalize(RutaColumnaEnum.VENDEDOR.getValor()));
        LinkedHashSet<String> fechas = new LinkedHashSet<>();

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            if (esFiltroEstructural(row, vendedorIdx, evaluator)) break;

            String fecha = ExcelUtil.getStringCell(row, fechaIdx, evaluator);
            if (fecha != null && !fecha.isBlank()) {
                fechas.add(fecha.trim());
            }
        }
        return new ArrayList<>(fechas);
    }

    /**
     * Lee todos los registros del Excel aplicando todos los filtros y la deduplicación.
     */
    private List<RegistroRutaDTO> leerRegistrosFiltrados(
            Sheet sheet,
            Map<String, Integer> idx,
            FormulaEvaluator evaluator,
            List<String> fechasSeleccionadas,
            Set<String> nombresExcluidos
    ) {
        Integer fechaIdx    = idx.get(TextoUtil.normalize(RutaColumnaEnum.FECHA.getValor()));
        Integer vendedorIdx = idx.get(TextoUtil.normalize(RutaColumnaEnum.VENDEDOR.getValor()));

        Set<String> nombresVistos = new HashSet<>();
        List<RegistroRutaDTO> resultado = new ArrayList<>();

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            // Límite estructural: desde TOTAL en adelante, parar
            if (esFiltroEstructural(row, vendedorIdx, evaluator)) break;

            String nombreVendedor = ExcelUtil.getStringCell(row, vendedorIdx, evaluator);
            if (nombreVendedor == null || nombreVendedor.isBlank()) continue;
            nombreVendedor = nombreVendedor.trim();

            // Exclusión por lista en BD
            if (nombresExcluidos.contains(TextoUtil.normalize(nombreVendedor))) continue;

            // Exclusión por color rojo en la celda VENDEDOR
            if (tieneCeldaRoja(row, vendedorIdx)) continue;

            // Filtro por fecha seleccionada
            String fecha = ExcelUtil.getStringCell(row, fechaIdx, evaluator);
            if (fecha == null || !fechasSeleccionadas.contains(fecha.trim())) continue;

            // Deduplicación: primera ocurrencia por nombre
            String claveDedup = TextoUtil.normalize(nombreVendedor);
            if (nombresVistos.contains(claveDedup)) {
                log.warn("Vendedor duplicado ignorado en fila {}: '{}'", i + 1, nombreVendedor);
                continue;
            }
            nombresVistos.add(claveDedup);

            resultado.add(construirRegistroDTO(row, idx, evaluator, nombreVendedor, fecha.trim(), i));
        }
        return resultado;
    }

    private RegistroRutaDTO construirRegistroDTO(
            Row row,
            Map<String, Integer> idx,
            FormulaEvaluator evaluator,
            String nombre,
            String fecha,
            int numeroFila
    ) {
        return RegistroRutaDTO.builder()
            .nombre(nombre)
            .fecha(fecha)
            .numeroFila(numeroFila)
            .deudaAnterior(ExcelUtil.getBigDecimalCell(row, idx.get(TextoUtil.normalize(RutaColumnaEnum.DEUDA_ANT.getValor())), evaluator))
            .seneteTotalEnviado(ExcelUtil.getIntCell(row, idx.get(TextoUtil.normalize(RutaColumnaEnum.SENETE_TOTAL_ENVIADO.getValor())), evaluator))
            .telebingoTotalEnviado(ExcelUtil.getIntCell(row, idx.get(TextoUtil.normalize(RutaColumnaEnum.TELEBINGO_TOTAL_ENVIADO.getValor())), evaluator))
            .refSenete(ExcelUtil.getIntCell(row, idx.get(TextoUtil.normalize(RutaColumnaEnum.REF_SENETE.getValor())), evaluator))
            .refTelb(ExcelUtil.getIntCell(row, idx.get(TextoUtil.normalize(RutaColumnaEnum.REF_TELB.getValor())), evaluator))
            .devSen(ExcelUtil.getIntCell(row, idx.get(TextoUtil.normalize(RutaColumnaEnum.DEV_SEN.getValor())), evaluator))
            .devTelb(ExcelUtil.getIntCell(row, idx.get(TextoUtil.normalize(RutaColumnaEnum.DEV_TELB.getValor())), evaluator))
            .pago1(ExcelUtil.getBigDecimalCell(row, idx.get(TextoUtil.normalize(RutaColumnaEnum.PAGO1.getValor())), evaluator))
            .pago2(ExcelUtil.getBigDecimalCell(row, idx.get(TextoUtil.normalize(RutaColumnaEnum.PAGO2.getValor())), evaluator))
            .build();
    }

    /**
     * Devuelve true si la fila corresponde al límite estructural:
     * primera celda VENDEDOR que contenga "TOTAL" (sin distinción de mayúsculas).
     */
    private boolean esFiltroEstructural(Row row, Integer vendedorIdx, FormulaEvaluator evaluator) {
        if (vendedorIdx == null) return false;
        String valor = ExcelUtil.getStringCell(row, vendedorIdx, evaluator);
        return valor != null && valor.trim().toUpperCase().contains("TOTAL");
    }

    /**
     * Devuelve true si la celda VENDEDOR de la fila tiene color de fondo rojo (FFFF0000).
     * Solo aplica a archivos XSSF (.xlsx). Para otros formatos devuelve false.
     */
    private boolean tieneCeldaRoja(Row row, Integer vendedorIdx) {
        if (vendedorIdx == null) return false;
        Cell cell = row.getCell(vendedorIdx);
        if (cell == null) return false;

        try {
            if (cell instanceof XSSFCell xssfCell) {
                XSSFColor color = xssfCell.getCellStyle().getFillForegroundColorColor();
                if (color != null && color.getARGBHex() != null) {
                    String argb = color.getARGBHex().toUpperCase();
                    // Rojo puro: FF en canal Alpha, FF en R, 00 en G, 00 en B
                    return argb.equals("FFFF0000");
                }
            }
        } catch (Exception e) {
            log.warn("No se pudo leer el color de la celda en fila {}: {}", row.getRowNum() + 1, e.getMessage());
        }
        return false;
    }
}
