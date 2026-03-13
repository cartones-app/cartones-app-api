package com.eliasgonzalez.cartones.ruta.service;

import com.eliasgonzalez.cartones.ruta.controller.dto.ExclusionRutaRequestDTO;
import com.eliasgonzalez.cartones.ruta.controller.dto.ExclusionRutaResponseDTO;
import com.eliasgonzalez.cartones.ruta.entity.ExclusionRuta;
import com.eliasgonzalez.cartones.ruta.repository.ExclusionRutaRepository;
import com.eliasgonzalez.cartones.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AdminExclusionRutaService {

    private final ExclusionRutaRepository exclusionRutaRepo;

    @Transactional(readOnly = true)
    public List<ExclusionRutaResponseDTO> listarTodas() {
        return exclusionRutaRepo.findAll().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ExclusionRutaResponseDTO> listarActivas() {
        return exclusionRutaRepo.findByActivoTrue().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    public ExclusionRutaResponseDTO crear(ExclusionRutaRequestDTO request) {
        if (exclusionRutaRepo.existsByNombreIgnoreCase(request.getNombre())) {
            throw new IllegalArgumentException("Ya existe una exclusión con el nombre: " + request.getNombre());
        }

        ExclusionRuta exclusion = ExclusionRuta.builder()
            .nombre(request.getNombre().trim())
            .descripcion(request.getDescripcion())
            .activo(request.getActivo() != null ? request.getActivo() : true)
            .build();

        return toDTO(exclusionRutaRepo.save(exclusion));
    }

    public ExclusionRutaResponseDTO actualizar(Long id, ExclusionRutaRequestDTO request) {
        ExclusionRuta exclusion = buscarPorId(id);
        exclusion.setNombre(request.getNombre().trim());
        exclusion.setDescripcion(request.getDescripcion());
        if (request.getActivo() != null) {
            exclusion.setActivo(request.getActivo());
        }
        return toDTO(exclusionRutaRepo.save(exclusion));
    }

    public void eliminar(Long id) {
        ExclusionRuta exclusion = buscarPorId(id);
        exclusionRutaRepo.delete(exclusion);
        log.info("Exclusión de ruta eliminada: {}", exclusion.getNombre());
    }

    private ExclusionRuta buscarPorId(Long id) {
        return exclusionRutaRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Exclusión de ruta con ID " + id + " no encontrada.", List.of()
            ));
    }

    private ExclusionRutaResponseDTO toDTO(ExclusionRuta e) {
        return ExclusionRutaResponseDTO.builder()
            .id(e.getId())
            .nombre(e.getNombre())
            .descripcion(e.getDescripcion())
            .activo(e.getActivo())
            .createdAt(e.getCreatedAt())
            .createdBy(e.getCreatedBy())
            .build();
    }
}
