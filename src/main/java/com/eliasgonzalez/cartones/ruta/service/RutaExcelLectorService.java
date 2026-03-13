package com.eliasgonzalez.cartones.ruta.service;

import com.eliasgonzalez.cartones.ruta.controller.dto.CargaRutaResponseDTO;
import com.eliasgonzalez.cartones.ruta.controller.dto.RegistroRutaDTO;
import com.eliasgonzalez.cartones.ruta.entity.SesionRuta;
import com.eliasgonzalez.cartones.ruta.entity.enums.RutaColumnaEnum;
import com.eliasgonzalez.cartones.ruta.repository.ExclusionRutaRepository;
import com.eliasgonzalez.cartones.ruta.repository.SesionRutaRepository;
import com.eliasgonzalez.cartones.shared.exception.ExcelProcessingException;
import com.eliasgonzalez.cartones.shared.exception.ResourceNotFoundException;
import com.eliasgonzalez.cartones.shared.util.Util;
import com.eliasgonzalez.cartones.vendedor.entity.Vendedor;
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
import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class RutaExcelLectorService {

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
            Sheet sheet = obtenerHoja(wb);
            Map<String, Integer> idx = construirIndiceColumnas(sheet);
            fechasDisponibles = extraerFechasUnicas(sheet, idx, wb.getCreationHelper().createFormulaEvaluator());
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

        // Actualizar la sesión con las fechas seleccionadas
        sesion.setFechaFiltro(String.join(",", fechasSeleccionadas));
        sesionRutaRepo.save(sesion);

        // Cargar exclusiones activas de BD (normalizado para comparación)
        Set<String> nombresExcluidos = new HashSet<>();
        exclusionRutaRepo.findByActivoTrue()
            .forEach(e -> nombresExcluidos.add(Util.normalize(e.getNombre())));

        List<RegistroRutaDTO> registros;
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(sesion.getArchivoExcel()))) {
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            evaluator.clearAllCachedResultValues();
            evaluator.evaluateAll();

            Sheet sheet = obtenerHoja(wb);
            Map<String, Integer> idx = construirIndiceColumnas(sheet);

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

        // Actualizar contadores de la sesión
        sesion.setTotalRegistros(registros.size());
        sesionRutaRepo.save(sesion);

        log.info("Sesión {}: {} registros filtrados para fechas {}", sesionId, registros.size(), fechasSeleccionadas);
        return registros;
    }

    // ---------------------------------------------------------------------------
    // Métodos privados de parseo
    // ---------------------------------------------------------------------------

    private Sheet obtenerHoja(Workbook wb) {
        int idx = wb.getSheetIndex(RutaColumnaEnum.HOJA.getValor());
        if (idx < 0) {
            throw new ExcelProcessingException(
                "La hoja '" + RutaColumnaEnum.HOJA.getValor() + "' no fue encontrada en el Excel.", List.of()
            );
        }
        return wb.getSheetAt(idx);
    }

    /**
     * Construye un mapa nombre_normalizado → índice de columna leyendo la fila de encabezado (fila 0).
     */
    private Map<String, Integer> construirIndiceColumnas(Sheet sheet) {
        Row header = sheet.getRow(0);
        if (header == null) {
            throw new ExcelProcessingException("El Excel no tiene fila de encabezados.", List.of());
        }
        Map<String, Integer> idx = new HashMap<>();
        for (Cell c : header) {
            if (c.getCellType() == CellType.STRING) {
                String nombre = c.getStringCellValue();
                if (nombre != null && !nombre.isBlank()) {
                    idx.put(Util.normalize(nombre), c.getColumnIndex());
                }
            }
        }
        validarEncabezados(idx);
        return idx;
    }

    private void validarEncabezados(Map<String, Integer> idx) {
        List<String> requeridos = List.of(
            RutaColumnaEnum.FECHA.getValor(),
            RutaColumnaEnum.VENDEDOR.getValor()
        );
        List<String> faltantes = new ArrayList<>();
        for (String col : requeridos) {
            if (!idx.containsKey(Util.normalize(col))) faltantes.add(col);
        }
        if (!faltantes.isEmpty()) {
            throw new ExcelProcessingException("Faltan columnas requeridas en el Excel: " + faltantes, List.of());
        }
    }

    /**
     * Extrae las fechas únicas de la columna FECHA, respetando el límite estructural (TOTAL).
     * Mantiene el orden de aparición.
     */
    private List<String> extraerFechasUnicas(Sheet sheet, Map<String, Integer> idx, FormulaEvaluator evaluator) {
        Integer fechaIdx = idx.get(Util.normalize(RutaColumnaEnum.FECHA.getValor()));
        Integer vendedorIdx = idx.get(Util.normalize(RutaColumnaEnum.VENDEDOR.getValor()));
        LinkedHashSet<String> fechas = new LinkedHashSet<>();

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            if (esFiltroEstructural(row, vendedorIdx, evaluator)) break;

            String fecha = Util.getStringCell(row, fechaIdx, evaluator);
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
        Integer fechaIdx    = idx.get(Util.normalize(RutaColumnaEnum.FECHA.getValor()));
        Integer vendedorIdx = idx.get(Util.normalize(RutaColumnaEnum.VENDEDOR.getValor()));

        Set<String> nombresVistos = new HashSet<>(); // para deduplicar por nombre
        List<RegistroRutaDTO> resultado = new ArrayList<>();

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            // Límite estructural: desde TOTAL en adelante, parar
            if (esFiltroEstructural(row, vendedorIdx, evaluator)) break;

            String nombreVendedor = Util.getStringCell(row, vendedorIdx, evaluator);
            if (nombreVendedor == null || nombreVendedor.isBlank()) continue;
            nombreVendedor = nombreVendedor.trim();

            // Exclusión por lista en BD
            if (nombresExcluidos.contains(Util.normalize(nombreVendedor))) continue;

            // Exclusión por color rojo en la celda VENDEDOR
            if (tieneCeldaRoja(row, vendedorIdx)) continue;

            // Filtro por fecha seleccionada
            String fecha = Util.getStringCell(row, fechaIdx, evaluator);
            if (fecha == null || !fechasSeleccionadas.contains(fecha.trim())) continue;

            // Deduplicación: primera ocurrencia por nombre
            String claveDedup = Util.normalize(nombreVendedor);
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
            .deudaAnterior(getBigDecimalCell(row, idx.get(Util.normalize(RutaColumnaEnum.DEUDA_ANT.getValor())), evaluator))
            .seneteTotalEnviado(Util.getIntCell(row, idx.get(Util.normalize(RutaColumnaEnum.SENETE_TOTAL_ENVIADO.getValor())), evaluator))
            .telebingoTotalEnviado(Util.getIntCell(row, idx.get(Util.normalize(RutaColumnaEnum.TELEBINGO_TOTAL_ENVIADO.getValor())), evaluator))
            .refSenete(Util.getIntCell(row, idx.get(Util.normalize(RutaColumnaEnum.REF_SENETE.getValor())), evaluator))
            .refTelb(Util.getIntCell(row, idx.get(Util.normalize(RutaColumnaEnum.REF_TELB.getValor())), evaluator))
            .devSen(Util.getIntCell(row, idx.get(Util.normalize(RutaColumnaEnum.DEV_SEN.getValor())), evaluator))
            .devTelb(Util.getIntCell(row, idx.get(Util.normalize(RutaColumnaEnum.DEV_TELB.getValor())), evaluator))
            .pago1(getBigDecimalCell(row, idx.get(Util.normalize(RutaColumnaEnum.PAGO1.getValor())), evaluator))
            .pago2(getBigDecimalCell(row, idx.get(Util.normalize(RutaColumnaEnum.PAGO2.getValor())), evaluator))
            .build();
    }

    /**
     * Devuelve true si la fila corresponde al límite estructural:
     * primera celda VENDEDOR que contenga "TOTAL" (mayúsculas/minúsculas indistinto).
     */
    private boolean esFiltroEstructural(Row row, Integer vendedorIdx, FormulaEvaluator evaluator) {
        if (vendedorIdx == null) return false;
        String valor = Util.getStringCell(row, vendedorIdx, evaluator);
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

    private BigDecimal getBigDecimalCell(Row row, Integer colIdx, FormulaEvaluator evaluator) {
        if (colIdx == null || row == null) return null;
        String valor = Util.getStringCell(row, colIdx, evaluator);
        if (valor == null || valor.isBlank()) return null;
        try {
            return new BigDecimal(valor.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
