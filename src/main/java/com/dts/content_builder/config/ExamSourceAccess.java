package com.dts.content_builder.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Set;

@Component
public class ExamSourceAccess {

    public static final String HEADER_NAME = "X-DTS-Exam-Source-Token";

    private static final String KEY_CONTEXT = "dts:content-builder:exam-source:v1";
    private static final String ISSUER = "dts-examination";
    private static final String SUBJECT = "exam-source-reader";
    private static final String AUDIENCE = "dts-content-builder";
    private static final String PURPOSE = "EXAM_SOURCE_READ";
    private static final String TOKEN_TYPE = "dts-service+jwt";
    private static final long MAX_LIFETIME_SECONDS = 60;
    private static final long CLOCK_SKEW_SECONDS = 5;

    private final JwtParser parser;

    public ExamSourceAccess(JwtProperties properties) {
        SecretKey serviceKey = deriveServiceKey(properties.getSecret());
        this.parser = Jwts.parser()
                .verifyWith(serviceKey)
                .requireIssuer(ISSUER)
                .requireSubject(SUBJECT)
                .require("purpose", PURPOSE)
                .clockSkewSeconds(CLOCK_SKEW_SECONDS)
                .build();
    }

    public void requireAccess(String serviceToken) {
        if (hasAdminReadPermission() || validServiceToken(serviceToken)) {
            return;
        }
        throw new AccessDeniedException("Access denied to internal exam question source");
    }

    private boolean hasAdminReadPermission() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        boolean canReadQuestions = authentication.getAuthorities().stream()
                .anyMatch(authority -> "PERM_questions:read".equals(authority.getAuthority()));
        return admin && canReadQuestions;
    }

    private boolean validServiceToken(String token) {
        if (token == null || token.isBlank() || token.length() > 4096) {
            return false;
        }
        try {
            Jws<Claims> signed = parser.parseSignedClaims(token);
            if (!"HS256".equals(signed.getHeader().getAlgorithm())
                    || !TOKEN_TYPE.equals(signed.getHeader().get("typ"))) {
                return false;
            }
            Claims claims = signed.getPayload();
            Set<String> audience = claims.getAudience();
            if (audience == null || audience.size() != 1 || !audience.contains(AUDIENCE)
                    || claims.getIssuedAt() == null || claims.getExpiration() == null
                    || claims.getId() == null || claims.getId().isBlank()) {
                return false;
            }
            Instant issuedAt = claims.getIssuedAt().toInstant();
            Instant expiresAt = claims.getExpiration().toInstant();
            Instant now = Instant.now();
            return !issuedAt.isAfter(now.plusSeconds(CLOCK_SKEW_SECONDS))
                    && expiresAt.isAfter(issuedAt)
                    && !expiresAt.isAfter(issuedAt.plusSeconds(MAX_LIFETIME_SECONDS))
                    && expiresAt.isAfter(now.minusSeconds(CLOCK_SKEW_SECONDS));
        } catch (JwtException | IllegalArgumentException exception) {
            // Do not log credentials or token payloads.
            return false;
        }
    }

    private SecretKey deriveServiceKey(String encodedSecret) {
        if (encodedSecret == null || encodedSecret.isBlank()) {
            throw new IllegalArgumentException("JWT secret must not be empty");
        }
        byte[] rootKey = Decoders.BASE64.decode(encodedSecret);
        if (rootKey.length < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 bytes");
        }
        try {
            // A separate derived key keeps service tokens distinct from user JWTs.
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(rootKey, "HmacSHA256"));
            byte[] derivedKey = mac.doFinal(KEY_CONTEXT.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(derivedKey, "HmacSHA256");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot initialize internal exam source authentication", exception);
        }
    }
}
