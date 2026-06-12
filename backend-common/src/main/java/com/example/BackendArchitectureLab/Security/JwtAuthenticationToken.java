package com.example.BackendArchitectureLab.Security;

import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.keys.HmacKey;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class JwtAuthenticationToken {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationToken.class);

    private final String jwtSecret;
    private final String jwtIssuer;
    private final String jwtAudience;
    private final long expirationMinutes;
    private final HmacKey secretKey;

    public JwtAuthenticationToken(String jwtSecret, String jwtIssuer, String jwtAudience, long expirationMinutes) {
        this.jwtSecret = jwtSecret;
        this.jwtIssuer = jwtIssuer;
        this.jwtAudience = jwtAudience;
        this.expirationMinutes = expirationMinutes;
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("jwt.secret.use must be configured");
        }
        this.secretKey = new HmacKey(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateJWT(String user) throws JoseException {
        HashMap<String, String> map = new HashMap<>();
        map.put("email", user);
        JwtClaims claims = generateClaims(map, expirationMinutes);
        JsonWebSignature jws = new JsonWebSignature();
        jws.setKey(secretKey);
        jws.setPayload(claims.toJson());
        jws.setAlgorithmHeaderValue("HS256");
        return jws.getCompactSerialization();
    }

    public JwtClaims generateClaims(HashMap<String, String> map, long expirationMinutes) {
        JwtClaims claims = new JwtClaims();
        claims.setExpirationTimeMinutesInTheFuture(expirationMinutes);
        claims.setIssuedAtToNow();
        if (jwtIssuer != null && !jwtIssuer.isBlank()) {
            claims.setIssuer(jwtIssuer);
        }
        if (jwtAudience != null && !jwtAudience.isBlank()) {
            claims.setAudience(jwtAudience);
        }
        String subject = map.get("email");
        if (subject != null && !subject.isBlank()) {
            claims.setSubject(subject);
        }
        map.forEach(claims::setStringClaim);
        return claims;
    }

    public JwtClaims verifyJWT(String token) throws InvalidJwtException {
        JwtConsumerBuilder builder = new JwtConsumerBuilder()
                .setRequireExpirationTime()
                .setVerificationKey(secretKey);
        if (jwtIssuer != null && !jwtIssuer.isBlank()) {
            builder.setExpectedIssuer(jwtIssuer);
        }
        if (jwtAudience != null && !jwtAudience.isBlank()) {
            builder.setExpectedAudience(jwtAudience);
        }
        JwtConsumer jwtConsumer = builder.build();
        try {
            JwtClaims jwtClaims = jwtConsumer.processToClaims(token);
            return jwtClaims;
        } catch (InvalidJwtException e) {
            log.warn("JWT verification failed: {}", e.toString());
            throw e;
        } catch (Exception e) {
            log.error("JWT verification error", e);
            throw e;
        }
    }
}
