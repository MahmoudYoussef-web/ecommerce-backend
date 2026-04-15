package com.mahmoud.ecommerce_backend.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.List;

@Component
public class JwtUtils {

    @Value("${auth.jwt.secret}")
    private String jwtSecret;

    @Value("${auth.jwt.expiration}")
    private long expiration;

    private Key key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    public String generateToken(Long userId,
                                String email,
                                List<String> roles,
                                Integer tokenVersion,
                                Long tenantId) {

        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .setSubject(email)
                .claim("uid", userId)
                .claim("roles", roles)
                .claim("tv", tokenVersion)
                .claim("tid", tenantId)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String extractEmail(String token) {
        return parse(token).getSubject();
    }

    public Long extractUserId(String token) {
        return parse(token).get("uid", Long.class);
    }

    public Integer extractTokenVersion(String token) {
        return parse(token).get("tv", Integer.class);
    }

    public Long extractTenantId(String token) {
        return parse(token).get("tid", Long.class);
    }

    public boolean validate(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }
}