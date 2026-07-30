package com.dts.content_builder.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.List;
import java.util.UUID;

@Component
public class JwtProvider {

    private final SecretKey accessKey;

    public JwtProvider(JwtProperties jwtProperties) {
        this.accessKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.getSecret()));
    }

    public Claims validateAccessToken(String token) {
        return Jwts.parser()
                .verifyWith(accessKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID getUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(Claims claims) {
        return claims.get("roles", List.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> getPermissions(Claims claims) {
        return claims.get("permissions", List.class);
    }
}
