package com.antigravity.mes.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and validates signed (HMAC-SHA256) session tokens carrying the
 * authenticated employee's id and role. Stateless - no server-side session store.
 * Used by {@link com.antigravity.mes.web.RoleAuthorizationInterceptor} to gate
 * activity-restricted endpoints and to resolve "who is calling" for self-service
 * endpoints (e.g. Profile).
 *
 * Also issues short-lived "role selection" tokens for employees with more than one
 * role: after password verification but before a role is chosen, the client holds
 * one of these (identity only, no role/activities) instead of the full session token.
 */
@Component
public class JwtService {

    private static final String PURPOSE_CLAIM = "purpose";
    private static final String ROLE_SELECTION_PURPOSE = "ROLE_SELECT";
    private static final long ROLE_SELECTION_MINUTES = 5;

    private final SecretKey signingKey;
    private final long expirationMinutes;

    public JwtService(
            @Value("${app.jwt.secret}") String secretBase64,
            @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secretBase64));
        this.expirationMinutes = expirationMinutes;
    }

    public String issueToken(Long employeeId, String role, Long factoryId, boolean isSuperAdmin) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMinutes * 60_000);
        return Jwts.builder()
                .subject(String.valueOf(employeeId))
                .claim("role", role)
                .claim("factory_id", factoryId)
                .claim("is_super_admin", isSuperAdmin)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /** Returns the token's principal (employee id + role + factory + super admin flag) if valid (signature + not expired), else empty. */
    public Optional<TokenPrincipal> validate(String token) {
        try {
            Claims claims = parseClaims(token);
            Long employeeId = Long.valueOf(claims.getSubject());
            String role = claims.get("role", String.class);
            Long factoryId = claims.get("factory_id", Long.class);
            Boolean isSuperAdmin = claims.get("is_super_admin", Boolean.class);
            return Optional.of(new TokenPrincipal(employeeId, role, factoryId, isSuperAdmin != null && isSuperAdmin));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** A short-lived token proving password verification succeeded, before a role has been chosen. */
    public String issueRoleSelectionToken(Long employeeId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ROLE_SELECTION_MINUTES * 60_000);
        return Jwts.builder()
                .subject(String.valueOf(employeeId))
                .claim(PURPOSE_CLAIM, ROLE_SELECTION_PURPOSE)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /** Returns the employee id if this is a valid, unexpired role-selection token, else empty. */
    public Optional<Long> validateRoleSelectionToken(String token) {
        try {
            Claims claims = parseClaims(token);
            if (!ROLE_SELECTION_PURPOSE.equals(claims.get(PURPOSE_CLAIM, String.class))) {
                return Optional.empty();
            }
            return Optional.of(Long.valueOf(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
