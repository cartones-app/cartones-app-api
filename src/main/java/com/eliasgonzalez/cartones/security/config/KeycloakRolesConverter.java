package com.eliasgonzalez.cartones.security.config;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Extrae los roles del claim {@code realm_access.roles} del JWT de Keycloak y
 * los convierte en {@link GrantedAuthority} de Spring Security con prefijo
 * {@code ROLE_}.
 *
 * <p><b>Normalización a UPPERCASE</b>: Spring's {@code hasRole("ADMIN")} compara
 * con prefijo + literal. Si Keycloak emite el rol con otra capitalización (ej.
 * {@code Admin} o {@code admin}), una comparación cruda fallaría. Normalizamos
 * a uppercase para que las expresiones SpEL del lado consumidor sean robustas
 * frente a cambios de capitalización en el realm.
 *
 * <p><b>Defensivo</b>: si el claim no existe, no es Map, o el array de roles
 * no es una List o contiene non-string, retorna empty list en lugar de NPE
 * o ClassCastException.
 */
public class KeycloakRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String CLAIM_REALM_ACCESS = "realm_access";
    private static final String CLAIM_ROLES = "roles";
    private static final String AUTHORITY_PREFIX = "ROLE_";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(CLAIM_REALM_ACCESS);
        if (realmAccess == null) {
            return List.of();
        }
        Object rolesRaw = realmAccess.get(CLAIM_ROLES);
        if (!(rolesRaw instanceof List<?> rolesList)) {
            return List.of();
        }
        return rolesList.stream()
                .filter(r -> r instanceof String)
                .map(r -> ((String) r).trim())
                .filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .map(s -> new SimpleGrantedAuthority(AUTHORITY_PREFIX + s))
                .collect(Collectors.toUnmodifiableList());
    }
}
