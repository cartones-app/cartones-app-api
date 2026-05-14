package com.eliasgonzalez.cartones.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRolesConverterTest {

    private final KeycloakRolesConverter converter = new KeycloakRolesConverter();

    @Test
    void convierteRolesDeRealmAccessAGrantedAuthoritiesConPrefijoROLE() {
        Jwt jwt = jwtConRealmAccess(List.of("ADMIN", "DISTRIBUIDOR"));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_DISTRIBUIDOR");
    }

    @Test
    void devuelveListaVaciaSiRealmAccessNoEstaPresente() {
        Jwt jwt = jwtSinRealmAccess();

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities).isEmpty();
    }

    @Test
    void devuelveListaVaciaSiRealmAccessNoTieneRoles() {
        Jwt jwt = jwtConClaim("realm_access", Map.of("otroCampo", "valor"));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities).isEmpty();
    }

    @Test
    void devuelveListaVaciaSiRolesEsListaVacia() {
        Jwt jwt = jwtConRealmAccess(List.of());

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities).isEmpty();
    }

    @Test
    void preservaCaseDelRol() {
        // Keycloak por convención usa MAYÚSCULAS en realm roles, pero validamos
        // que el converter no normalice — Spring matchea hasRole("ADMIN") con
        // ROLE_ADMIN exacto, así que cualquier transformación rompería auth.
        Jwt jwt = jwtConRealmAccess(List.of("admin", "Distribuidor"));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_admin", "ROLE_Distribuidor");
    }

    private static Jwt jwtConRealmAccess(List<String> roles) {
        return jwtConClaim("realm_access", Map.of("roles", roles));
    }

    private static Jwt jwtConClaim(String name, Object value) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .subject("test-user")
                .claim(name, value)
                .build();
    }

    private static Jwt jwtSinRealmAccess() {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .subject("test-user")
                .claim("preferred_username", "tester")
                .build();
    }
}
