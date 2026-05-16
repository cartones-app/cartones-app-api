package com.eliasgonzalez.cartones.distribucion.preferencias.service;

import com.eliasgonzalez.cartones.distribucion.domain.enums.LayoutEtiqueta;
import com.eliasgonzalez.cartones.distribucion.domain.enums.OrdenEtiqueta;
import com.eliasgonzalez.cartones.distribucion.preferencias.domain.PreferenciasDistribuidor;
import com.eliasgonzalez.cartones.distribucion.preferencias.repository.PreferenciasDistribuidorRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Lectura y escritura de preferencias de impresión de etiquetas por
 * distribuidor.
 *
 * <p>Convención: el {@code username} es el {@code sub} del JWT de Keycloak —
 * mismo string que termina en {@code created_by} de las entidades auditables.
 *
 * <p>Defaults: cuando no existe row en la tabla, las preferencias caen a
 * {@link PreferenciasResueltas#defaults()}. Eso preserva el comportamiento
 * previo a la feature para distribuidores que nunca abrieron la pantalla
 * de preferencias.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PreferenciasDistribuidorService {

    private final PreferenciasDistribuidorRepository repo;

    /**
     * Devuelve las preferencias del distribuidor o los defaults si no hay row.
     * Siempre devuelve un valor no-null — pensado para el generador de PDF.
     */
    @Transactional(readOnly = true)
    public PreferenciasResueltas obtenerOPorDefecto(String username) {
        if (username == null || username.isBlank()) {
            return PreferenciasResueltas.defaults();
        }
        return repo.findById(username)
                .map(p -> new PreferenciasResueltas(p.getLayoutEtiqueta(), p.getOrdenEtiqueta()))
                .orElseGet(PreferenciasResueltas::defaults);
    }

    /**
     * Lee la entidad completa (con audit + preferredUsername) — para la UI
     * admin que necesita ver el username humano. Empty si no hay row.
     */
    @Transactional(readOnly = true)
    public Optional<PreferenciasDistribuidor> buscarPorUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return repo.findById(username);
    }

    /** Lista todas las preferencias (UI admin). */
    @Transactional(readOnly = true)
    public List<PreferenciasDistribuidor> listarTodas() {
        return repo.findAll();
    }

    /**
     * Upsert. Si existe row, actualiza; sino la crea.
     */
    @Transactional
    public PreferenciasDistribuidor guardar(
            String username,
            LayoutEtiqueta layout,
            OrdenEtiqueta orden) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username no puede ser nulo o vacío");
        }
        PreferenciasDistribuidor pref = repo.findById(username).orElseGet(() -> {
            PreferenciasDistribuidor nuevo = new PreferenciasDistribuidor();
            nuevo.setUsername(username);
            return nuevo;
        });
        pref.setLayoutEtiqueta(layout != null ? layout : LayoutEtiqueta.defaultValue());
        pref.setOrdenEtiqueta(orden != null ? orden : OrdenEtiqueta.defaultValue());
        return repo.save(pref);
    }

    /**
     * Tupla immutable de las dos preferencias resueltas. Se devuelve siempre
     * con valores no-null (defaults cuando aplica) para simplificar a los
     * callers que solo necesitan generar el PDF.
     */
    public record PreferenciasResueltas(LayoutEtiqueta layout, OrdenEtiqueta orden) {

        public static PreferenciasResueltas defaults() {
            return new PreferenciasResueltas(LayoutEtiqueta.defaultValue(), OrdenEtiqueta.defaultValue());
        }
    }
}
