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
 * <p>Edge case de colisión: si el nuevo username YA tiene row en
 * {@code preferencias_distribuidor} (otro user había usado ese nombre antes,
 * o reuse de nombre), no se puede UPDATE de la PK. En ese caso se BORRA la
 * row vieja — su contenido es inaccesible para el user (que ahora se identifica
 * con el nombre nuevo) y dejarla huérfana acumularía estado inconsistente.
 *
 *
 * <p>Concurrencia: el filtro tira siempre {@code REQUIRES_NEW} para aislar
 * la transacción del rename de la request principal; un error acá no debe
 * tumbar la request en curso.
 *
 * <p>Limitación conocida — JWTs concurrentes con username distinto: si dos
 * requests del mismo {@code sub} llegan simultáneamente con un JWT viejo
 * (preferred_username=A) y uno nuevo (B), la primera podría revertir el
 * rename de la otra. Probabilidad muy baja (ventana de segundos entre
 * refresh del JWT y propagación) y se autoresuelve en el siguiente request
 * con el JWT nuevo. No vale agregar locking pesimista para esto.
 *
 * <p>Rename inverso (volver al nombre anterior): soportado naturalmente. Si
 * un user pasa de A → B y luego B → A, el segundo rename detecta el cambio
 * y propaga normalmente. {@code renameCount} se incrementa cada vez.
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
        final String NUEVO = "nuevo";
        final String VIEJO = "viejo";

        // 1. preferencias_distribuidor: la PK es username, requiere manejo
        //    especial. Sin colisión: UPDATE in-place. Con colisión: DELETE
        //    de la row vieja (inaccesible para el user nuevo).
        Number existentes = (Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM preferencias_distribuidor WHERE username = :u")
                .setParameter("u", newUsername)
                .getSingleResult();
        long existeNueva = existentes.longValue();
        if (existeNueva == 0) {
            int actualizadas = em.createNativeQuery(
                            "UPDATE preferencias_distribuidor SET username = :nuevo WHERE username = :viejo")
                    .setParameter(NUEVO, newUsername)
                    .setParameter(VIEJO, oldUsername)
                    .executeUpdate();
            if (actualizadas > 0) {
                log.info("preferencias_distribuidor: renombrado username '{}' -> '{}' ({} row)",
                        oldUsername, newUsername, actualizadas);
            }
        } else {
            int borradas = em.createNativeQuery(
                            "DELETE FROM preferencias_distribuidor WHERE username = :viejo")
                    .setParameter(VIEJO, oldUsername)
                    .executeUpdate();
            log.warn("preferencias_distribuidor: ya existe row para '{}', borradas {} row(s) huérfana(s) de '{}'.",
                    newUsername, borradas, oldUsername);
        }

        // 2. created_by / modified_by en todas las tablas auditables.
        for (String tabla : AUDIT_TABLES) {
            int updatedCreated = em.createNativeQuery(
                            "UPDATE " + tabla + " SET created_by = :nuevo WHERE created_by = :viejo")
                    .setParameter(NUEVO, newUsername)
                    .setParameter(VIEJO, oldUsername)
                    .executeUpdate();
            int updatedModified = em.createNativeQuery(
                            "UPDATE " + tabla + " SET modified_by = :nuevo WHERE modified_by = :viejo")
                    .setParameter(NUEVO, newUsername)
                    .setParameter(VIEJO, oldUsername)
                    .executeUpdate();
            if (updatedCreated + updatedModified > 0) {
                log.info("{}: renombradas {} filas (created_by) + {} filas (modified_by)",
                        tabla, updatedCreated, updatedModified);
            }
        }
    }
}
