package br.edu.ufersa.cc.seg.common.auth;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import br.edu.ufersa.cc.seg.common.utils.InstanceType;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class TokenService {

    private final String secret;

    public String generateToken(final String identifier, final InstanceType instanceType) {
        return JWT.create()
                .withIssuer("smart-stations")
                .withSubject(identifier)
                .withClaim("instanceType", instanceType.name())
                .withExpiresAt(generateExpirationDate())
                .sign(Algorithm.HMAC256(secret));
    }

    public String validateToken(final String token, final InstanceType instanceType) {
        return JWT.require(Algorithm.HMAC256(secret))
                .withIssuer("smart-stations")
                .withClaim("instanceType", instanceType.name())
                .build()
                .verify(token)
                .getSubject();
    }

    private Instant generateExpirationDate() {
        return LocalDateTime.now().plusDays(1).toInstant(ZoneOffset.ofHours(-3));
    }

}
