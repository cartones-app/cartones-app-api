package com.eliasgonzalez.cartones.excel.service;

import com.eliasgonzalez.cartones.excel.enums.ExcelEnum;
import com.eliasgonzalez.cartones.excel.interfaces.IExcelService;
import com.eliasgonzalez.cartones.shared.exception.ExcelProcessingException;
import com.eliasgonzalez.cartones.shared.exception.FileProcessingException;
import com.eliasgonzalez.cartones.shared.util.Util;
import com.eliasgonzalez.cartones.vendedor.dto.FilasIgnoradasDTO;
import com.eliasgonzalez.cartones.vendedor.dto.VendedorExcelDTO;
import com.eliasgonzalez.cartones.vendedor.entity.ProcesoDistribucionVendedor;
import com.eliasgonzalez.cartones.vendedor.entity.Vendedor;
import com.eliasgonzalez.cartones.vendedor.repository.ProcesoDistribucionVendedorRepository;
import com.eliasgonzalez.cartones.vendedor.repository.VendedorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio encargado de la orquestación (I/O) y persistencia de datos
 * desde un archivo Excel de distribución, aplicando un enfoque "Todo o Nada".
 * Por cada fila válida: upsert en la tabla vendedor (maestro) +
 * creación de ProcesoDistribucionVendedor (datos de este proceso).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelService implements IExcelService {

    private final VendedorRepository vendedorRepo;
    private final ProcesoDistribucionVendedorRepository procesoVendedorRepo;
    private final ExcelValidationService validationService;

    @Override
    @Transactional
    public FilasIgnoradasDTO leerExcel(MultipartFile file, String procesoIdCreado) {

        log.info("Iniciando procesamiento del archivo Excel: {}", file.getOriginalFilename());

        List<String> erroresGlobales = new ArrayList<>();
        List<ProcesoDistribucionVendedor> registrosParaGuardar = new ArrayList<>();
        List<String> filasIgnoradas = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook wb = WorkbookFactory.create(is)) {

            // 1. CONFIGURACIÓN INICIAL + EVALUADOR
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            evaluator.clearAllCachedResultValues();
            evaluator.evaluateAll();

            int sheetIndex = wb.getSheetIndex(ExcelEnum.HOJA_SISTEMA_ETIQUETAS.getValue());
            if (sheetIndex < 0) {
                throw new ExcelProcessingException(
                    "La hoja ('" + ExcelEnum.HOJA_SISTEMA_ETIQUETAS.getValue() + "') no fue encontrada.",
                    List.of()
                );
            }
            Sheet sheet = wb.getSheetAt(sheetIndex);

            Row header = sheet.getRow(0);
            if (header == null) {
                throw new ExcelProcessingException("El archivo Excel está vacío o no tiene encabezados.", List.of());
            }

            Map<String, Integer> idx = new HashMap<>();
            for (Cell c : header) {
                String name = c.getStringCellValue();
                if (name != null) idx.put(Util.normalize(name), c.getColumnIndex());
            }

            validateHeader(idx);

            Integer vIdx = idx.get(Util.normalize(ExcelEnum.VENDEDOR.getValue()));

            // 2. LECTURA Y VALIDACIÓN
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                int filaActual = i + 1;
                Row row = sheet.getRow(i);

                if (Util.isRowEmpty(row, vIdx, evaluator)) {
                    filasIgnoradas.add("Fila " + filaActual + ": Vacía o sin nombre de vendedor. Omitiendo.");
                    log.warn("Se omite la fila {} porque el nombre del vendedor está vacío. ProcesoId: {}", filaActual, procesoIdCreado);
                    continue;
                }

                try {
                    VendedorExcelDTO dto = mapearFilaADTO(idx, row, filaActual, evaluator);
                    List<String> erroresFila = validationService.validate(dto);

                    if (!erroresFila.isEmpty()) {
                        erroresGlobales.addAll(erroresFila);
                    } else {
                        // Upsert del vendedor maestro + crear entrada de proceso
                        ProcesoDistribucionVendedor registro = resolverVendedorYCrearRegistro(dto, procesoIdCreado);
                        registrosParaGuardar.add(registro);
                    }

                } catch (Exception e) {
                    String errorMessage = String.format("Fila %d: Error inesperado al procesar: %s", filaActual, e.getMessage());
                    log.error(errorMessage);
                    erroresGlobales.add(errorMessage);
                }
            }

            // 3. DECISIÓN FINAL (TODO O NADA)
            if (!erroresGlobales.isEmpty()) {
                log.warn("Se detectaron {} errores. Abortando operación sin guardar nada.", erroresGlobales.size());
                throw new ExcelProcessingException(
                    "El archivo Excel contiene errores. No se ha guardado ningún dato.",
                    erroresGlobales
                );
            }

            if (!registrosParaGuardar.isEmpty()) {
                procesoVendedorRepo.saveAll(registrosParaGuardar);
                log.info("Se han guardado exitosamente {} registros.", registrosParaGuardar.size());
            }

            if (!filasIgnoradas.isEmpty()) {
                FilasIgnoradasDTO filasIgnoradasDTO = FilasIgnoradasDTO.builder()
                    .filasIgnoradas(filasIgnoradas)
                    .procesoId(procesoIdCreado)
                    .build();
                log.info(filasIgnoradasDTO.toString());
                return filasIgnoradasDTO;
            }

            return FilasIgnoradasDTO.builder().procesoId(procesoIdCreado).build();

        } catch (ExcelProcessingException | FileProcessingException e) {
            log.error("[INTERNO] Fallo en el procesamiento del Excel. Errores: {}. Mensaje: {}", erroresGlobales, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Fallo crítico en el procesamiento del Excel", e);
            throw new RuntimeException("Error al procesar el archivo Excel: " + e.getMessage(), e);
        }
    }

    /**
     * Busca el vendedor en la tabla maestra por nombre (case-insensitive).
     * Si no existe, lo crea. Luego construye el ProcesoDistribucionVendedor
     * con los datos de este proceso.
     */
    private ProcesoDistribucionVendedor resolverVendedorYCrearRegistro(VendedorExcelDTO dto, String procesoId) {
        String nombreNormalizado = dto.getNombre().trim();

        Vendedor vendedor = vendedorRepo.findByNombreIgnoreCase(nombreNormalizado)
            .orElseGet(() -> vendedorRepo.save(
                Vendedor.builder().nombre(nombreNormalizado).build()
            ));

        String deudaStr = dto.getDeudaStr();
        BigDecimal deuda = (deudaStr == null || deudaStr.isBlank())
            ? BigDecimal.ZERO
            : new BigDecimal(deudaStr.trim());

        return ProcesoDistribucionVendedor.builder()
            .vendedor(vendedor)
            .procesoId(procesoId)
            .cantidadSenete(dto.getCantidadSenete())
            .resultadoSenete(dto.getResultadoSenete())
            .cantidadTelebingo(dto.getCantidadTelebingo())
            .resultadoTelebingo(dto.getResultadoTelebingo())
            .deuda(deuda)
            .build();
    }

    private static void validateHeader(Map<String, Integer> idx) {
        String[] required = {
            ExcelEnum.VENDEDOR.getValue(),
            ExcelEnum.SALDO.getValue(),
            ExcelEnum.CANT_SENETE.getValue(),
            ExcelEnum.RESULT_SENETE.getValue(),
            ExcelEnum.CANT_TELEBINGO.getValue(),
            ExcelEnum.RESULT_TELEBINGO.getValue()
        };
        List<String> faltantes = new ArrayList<>();
        for (String h : required) {
            if (!idx.containsKey(Util.normalize(h))) faltantes.add(h);
        }
        if (!faltantes.isEmpty()) {
            throw new ExcelProcessingException("Faltan encabezados requeridos: " + faltantes, List.of());
        }
    }

    private static VendedorExcelDTO mapearFilaADTO(Map<String, Integer> idx, Row row, int filaActual, FormulaEvaluator evaluator) {
        return VendedorExcelDTO.builder()
            .nombre(Util.getStringCell(row, idx.get(Util.normalize(ExcelEnum.VENDEDOR.getValue())), evaluator))
            .deudaStr(Util.getStringCell(row, idx.get(Util.normalize(ExcelEnum.SALDO.getValue())), evaluator))
            .cantidadSenete(Util.getIntCell(row, idx.get(Util.normalize(ExcelEnum.CANT_SENETE.getValue())), evaluator))
            .resultadoSenete(Util.getIntCell(row, idx.get(Util.normalize(ExcelEnum.RESULT_SENETE.getValue())), evaluator))
            .cantidadTelebingo(Util.getIntCell(row, idx.get(Util.normalize(ExcelEnum.CANT_TELEBINGO.getValue())), evaluator))
            .resultadoTelebingo(Util.getIntCell(row, idx.get(Util.normalize(ExcelEnum.RESULT_TELEBINGO.getValue())), evaluator))
            .filaActual(filaActual)
            .build();
    }
}
