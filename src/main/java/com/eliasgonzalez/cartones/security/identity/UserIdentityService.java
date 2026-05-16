package com.eliasgonzalez.cartones.security.identity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Detecta y propaga renames de usuarios de Keycloak.
 *
 * <p>Patrón: el {@code sub} del JWT es el identificador estable (UUID). El
 * {@code preferred_username} es humano y mutable. Esta clase mantiene la
 * tabla {@code user_identity} con el último username conocido por sub y,
 * cuando detecta cambio, reescribe las referencias en las tablas locales
 * que indexan por username:
 *
 * <ul>
 *   <li>{@code preferencias_distribuidor.username} (PK).</li>
 *   <li>{@code created_by} / {@code modified_by} de todas las tablas
 *       auditables (8 tablas en V7): flag_override, proceso_distribucion,
 *       proceso_distribucion_vendedor, vendedor, sesion_ruta,
 *       sesion_ruta_registro, exclusion_ruta, preferencias_distribuidor.</li>
 * </ul>
 *
 * <p>Edge case: si el nuevo username ya tiene row en
 * {@code preferencias_distribuidor} (colisión por reuse), NO se renombra esa
 * row para no violar PK; el resto sí se actualiza. La preferencia vieja
 * queda huérfana y el filtro la limpia en background.
 *
 * <p>Concurrencia: el filtro tira siempre {@code REQUIRES_NEW} para aislar
 * la transacción del rename de la request principal; un error acá no debe
 * tumbar la request en curso.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserIdentityService {

    /** Tablas auditables (con created_by / modified_by). Mantener sincronizada con V7. */
    private static final List<String> AUDIT_TABLES = List.of(
            "flag_override",
            "proceso_distribucion",
            "proceso_distribucion_vendedor",
            "vendedor",
            "sesion_ruta",
            "sesion_ruta_registro",
            "exclusion_ruta",
            "preferencias_distribuidor"
    );

    private final UserIdentityRepository repo;

    @PersistenceContext
    private EntityManager em;

    /**
     * Llamado por {@link UserIdentityTrackingFilter} en cada request autenticada.
     * Operación idempotente: si el username no cambió solo actualiza {@code lastSeenAt}.
     * Si cambió, propaga el rename y bumpea {@code renameCount}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarUsoYPropagarSiCambio(String sub, String currentPreferredUsername) {
        if (sub == null || sub.isBlank() || currentPreferredUsername == null || currentPreferredUsername.isBlank()) {
            return;
        }
        Optional<UserIdentity> existente = repo.findById(sub);
        if (existente.isEmpty()) {
            crearNuevo(sub, currentPreferredUsername);
            return;
        }
        UserIdentity ui = existente.get();
        if (currentPreferredUsername.equals(ui.getCurrentPreferredUsername())) {
            ui.setLastSeenAt(LocalDateTime.now());
            return;
        }
        // Rename detectado.
        String anterior = ui.getCurrentPreferredUsername();
        log.warn("Detected rename for sub={}: '{}' -> '{}'. Propagando a {} tablas.",
                sub, anterior, currentPreferredUsername, AUDIT_TABLES.size());
        propagarRename(anterior, currentPreferredUsername);
        ui.setCurrentPreferredUsername(currentPreferredUsername);
        ui.setLastSeenAt(LocalDateTime.now());
        ui.setRenameCount(ui.getRenameCount() + 1);
    }

    private void crearNuevo(String sub, String preferredUsername) {
        UserIdentity ui = UserIdentity.builder()
                .sub(sub)
                .currentPreferredUsername(preferredUsername)
                .firstSeenAt(LocalDateTime.now())
                .lastSeenAt(LocalDateTime.now())
                .renameCount(0)
                .build();
        try {
            repo.save(ui);
        } catch (DataIntegrityViolationException e) {
            // Carrera: otro request del mismo user llegó primero. OK, ignorar.
            log.debug("Race insertando user_identity sub={}, ignorando", sub);
        }
    }

    private void propagarRename(String oldUsername, String newUsername) {
        // 1. preferencias_distribuidor: la PK es username, hay que UPDATE
        //    o, si ya existe row para newUsername, dejar la vieja huérfana
        //    (la limpia el filter en background — TODO opcional).
        long existeNueva = (Long) em.createNativeQuery(
                        "SELECT COUNT(*) FROM preferencias_distribuidor WHERE username = :u")
                .setParameter("u", newUsername)
                .getSingleResult();
        if (existeNueva == 0) {
            int actualizadas = em.createNativeQuery(
                            "UPDATE preferencias_distribuidor SET username = :nuevo WHERE username = :viejo")
                    .setParameter("nuevo", newUsername)
                    .setParameter("viejo", oldUsername)
                    .executeUpdate();
            if (actualizadas > 0) {
                log.info("preferencias_distribuidor: renombrado username '{}' -> '{}' ({} row)",
                        oldUsername, newUsername, actualizadas);
            }
        } else {
            log.warn("preferencias_distribuidor: ya existe row para '{}'. La row vieja de '{}' queda huérfana.",
                    newUsername, oldUsername);
        }

        // 2. created_by / modified_by en todas las tablas auditables.
        for (String tabla : AUDIT_TABLES) {
            int updatedCreated = em.createNativeQuery(
                            "UPDATE " + tabla + " SET created_by = :nuevo WHERE created_by = :viejo")
                    .setParameter("nuevo", newUsername)
                    .setParameter("viejo", oldUsername)
                    .executeUpdate();
            int updatedModified = em.createNativeQuery(
                            "UPDATE " + tabla + " SET modified_by = :nuevo WHERE modified_by = :viejo")
                    .setParameter("nuevo", newUsername)
                    .setParameter("viejo", oldUsername)
                    .executeUpdate();
            if (updatedCreated + updatedModified > 0) {
                log.info("{}: renombradas {} filas (created_by) + {} filas (modified_by)",
                        tabla, updatedCreated, updatedModified);
            }
        }
    }
}
