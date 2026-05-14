package com.eliasgonzalez.cartones.ruta.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.eliasgonzalez.cartones.common.exception.ResourceNotFoundException;
import com.eliasgonzalez.cartones.common.exception.UnprocessableEntityException;
import com.eliasgonzalez.cartones.common.logging.LogSanitizer;
import com.eliasgonzalez.cartones.ruta.controller.dto.SesionRutaRegistroResponseDTO;
import com.eliasgonzalez.cartones.ruta.controller.dto.SesionRutaResponseDTO;
import com.eliasgonzalez.cartones.ruta.domain.SesionRuta;
import com.eliasgonzalez.cartones.ruta.domain.SesionRutaRegistro;
import com.eliasgonzalez.cartones.ruta.domain.enums.EstadoSesionEnum;
import com.eliasgonzalez.cartones.ruta.repository.SesionRutaRegistroRepository;
import com.eliasgonzalez.cartones.ruta.repository.SesionRutaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AdminSesionRutaService {

    private final SesionRutaRepository sesionRutaRepo;
    private final SesionRutaRegistroRepository registroRepo;

    @Transactional(readOnly = true)
    public List<SesionRutaResponseDTO> listarSesiones(String estado, String createdBy) {
        return sesionRutaRepo.findAll().stream()
                .filter(s -> !StringUtils.hasText(estado) || s.getEstado().equalsIgnoreCase(estado))
                .filter(s -> !StringUtils.hasText(createdBy)
                        || (s.getCreatedBy() != null && s.getCreatedBy().equalsIgnoreCase(createdBy)))
                .map(this::toSesionDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SesionRutaResponseDTO obtenerSesion(String sesionId) {
        return toSesionDTO(buscarSesionPorSesionId(sesionId));
    }

    @Transactional(readOnly = true)
    public List<SesionRutaRegistroResponseDTO> listarRegistros(
            String sesionId, Boolean completado, String vendedorNombre, Boolean camposIncompletos) {
        SesionRuta sesion = buscarSesionPorSesionId(sesionId);

        List<SesionRutaRegistro> registros;
        if (Boolean.TRUE.equals(camposIncompletos)) {
            registros = registroRepo.findConCamposIncompletos(sesion.getId());
        } else {
            registros = registroRepo.findAllBySesionRutaId(sesion.getId());
        }

        return registros.stream()
                .filter(r -> completado == null || completado.equals(r.getCompletado()))
                .filter(r -> !StringUtils.hasText(vendedorNombre)
                        || (r.getVendedor() != null
                                && r.getVendedor().getNombre().toLowerCase().contains(vendedorNombre.toLowerCase())))
                .map(this::toRegistroDTO)
                .collect(Collectors.toList());
    }

    public void eliminarSesion(String sesionId) {
        SesionRuta sesion = buscarSesionPorSesionId(sesionId);

        if (EstadoSesionEnum.ACTIVA.getValor().equals(sesion.getEstado())) {
            throw new UnprocessableEntityException(
                    "No se puede eliminar una sesión activa.",
                    List.of("La sesión " + sesionId + " está en estado ACTIVA"));
        }

        sesionRutaRepo.delete(sesion);
        log.info("Sesión de ruta eliminada: {}", LogSanitizer.safe(sesionId));
    }

    public void eliminarSesiones(List<String> sesionIds) {
        sesionIds.forEach(this::eliminarSesion);
    }

    public void eliminarRegistro(Long registroId) {
        SesionRutaRegistro registro = registroRepo
                .findById(registroId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Registro de ruta con ID " + registroId + " no encontrado.", List.of()));
        registroRepo.delete(registro);
        log.info("Registro de sesión de ruta eliminado: {}", registroId);
    }

    private SesionRuta buscarSesionPorSesionId(String sesionId) {
        return sesionRutaRepo
                .findBySesionId(sesionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Sesión de ruta con ID " + sesionId + " no encontrada.", List.of()));
    }

    private SesionRutaResponseDTO toSesionDTO(SesionRuta s) {
        return SesionRutaResponseDTO.builder()
                .id(s.getId())
                .sesionId(s.getSesionId())
                .fechaFiltro(s.getFechaFiltro())
                .estado(s.getEstado())
                .totalRegistros(s.getTotalRegistros())
                .registrosCompletados(s.getRegistrosCompletados())
                .createdAt(s.getCreatedAt())
                .createdBy(s.getCreatedBy())
                .build();
    }

    private SesionRutaRegistroResponseDTO toRegistroDTO(SesionRutaRegistro r) {
        return SesionRutaRegistroResponseDTO.builder()
                .id(r.getId())
                .vendedorNombre(r.getVendedor() != null ? r.getVendedor().getNombre() : null)
                .fecha(r.getFecha())
                .seneteTotalEnviado(r.getSeneteTotalEnviado())
                .telebingoTotalEnviado(r.getTelebingoTotalEnviado())
                .refSenete(r.getRefSenete())
                .refTelb(r.getRefTelb())
                .devSen(r.getDevSen())
                .devTelb(r.getDevTelb())
                .pago1(r.getPago1())
                .pago2(r.getPago2())
                .nota(r.getNota())
                .completado(r.getCompletado())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
