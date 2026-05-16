package com.eliasgonzalez.cartones.security.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Mapeo {@code sub} (UUID estable de Keycloak) → {@code preferredUsername}
 * (username humano, mutable). Se mantiene actualizado por
 * {@link UserIdentityTrackingFilter}: en cada request autenticada, si el
 * {@code preferred_username} del JWT cambió respecto al guardado, propaga
 * el rename a las columnas {@code created_by} / {@code modified_by} y a
 * {@code preferencias_distribuidor.username}.
 *
 * <p>No extiende {@link com.eliasgonzalez.cartones.common.audit.EntidadAuditable}
 * a propósito: las columnas {@code first_seen_at} / {@code last_seen_at}
 * tienen semántica distinta (rastreo, no auditoría de quién modificó).
 */
@Entity
@Table(name = "user_identity")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class UserIdentity {

    @Id
    @Column(name = "sub", length = 64, nullable = false)
    private String sub;

    @Column(name = "current_preferred_username", length = 255, nullable = false)
    private String currentPreferredUsername;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "rename_count", nullable = false)
    private Integer renameCount;
}
