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
    void normalizaACaseInsensitiveAUpperCase() {
        // El converter normaliza los nombres de rol a UPPERCASE: todas las
        // expresiones hasRole(...) del backend usan UPPERCASE (ADMIN, DISTRIBUIDOR)
        // y Spring compara authorities case-sensitive. Si Keycloak emite el rol
        // con otra capitalización (admin, Distribuidor) sin normalización, el
        // match falla silenciosamente y el endpoint devuelve 403.
        Jwt jwt = jwtConRealmAccess(List.of("admin", "Distribuidor"));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_DISTRIBUIDOR");
    }

    @Test
    void ignoraValoresNoStringYRolesVacios() {
        // Defensa ante un realm_access malformado: roles puede contener
        // valores no-string o strings vacíos/whitespace tras un upgrade
        // de Keycloak o un realm corrupto.
        Jwt jwt = jwtConClaim("realm_access", Map.of("roles", List.of("ADMIN", "", "   ", 42)));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
    }

    @Test
    void devuelveListaVaciaSiRolesNoEsLista() {
        // Cast defensivo: si Keycloak devuelve un String donde esperamos List,
        // no debe lanzar ClassCastException.
        Jwt jwt = jwtConClaim("realm_access", Map.of("roles", "no-soy-una-lista"));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities).isEmpty();
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
