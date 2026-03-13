package com.eliasgonzalez.cartones.security.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

/**
 * Servicio para gestión de tokens JWT (JSON Web Tokens)
 * Responsable de la generación, validación y extracción de información de tokens.
 */
@Service
@Slf4j
public class JwtService {

    @Value("${app.security.jwt.secret-key}")
    private String secretKey;

    @Value("${app.security.jwt.expiration-ms}")
    private long jwtExpiration;

    /**
     * Extrae el subject (username/identificador) del token JWT
     */
    public String extractSubject(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extrae un claim específico del token usando una función
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Genera un token JWT con claims adicionales
     * @param extraClaims Claims personalizados para incluir en el token
     * @param subject Identificador del usuario (username, email, etc.)
     */
    public String generateToken(Map<String, Object> extraClaims, String subject) {
        long currentTimeMillis = System.currentTimeMillis();

        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(new Date(currentTimeMillis))
                .expiration(new Date(currentTimeMillis + jwtExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Genera un token JWT simple sin claims adicionales
     * @param subject Identificador del usuario
     */
    public String generateToken(String subject) {
        return generateToken(Map.of(), subject);
    }

    /**
     * Valida si el token es válido (firma correcta y no expirado)
     * @param token Token JWT a validar
     * @return true si el token es válido
     */
    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            log.error("Token JWT inválido: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Verifica si el token ha expirado
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Extrae la fecha de expiración del token
     */
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Extrae todos los claims del token
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Obtiene la clave de firma a partir del secreto configurado
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
