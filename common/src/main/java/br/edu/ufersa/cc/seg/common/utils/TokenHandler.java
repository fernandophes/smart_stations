package br.edu.ufersa.cc.seg.common.utils;

import java.util.Optional;
import java.util.function.BiFunction;

import com.auth0.jwt.exceptions.JWTVerificationException;

import br.edu.ufersa.cc.seg.common.auth.TokenService;
import br.edu.ufersa.cc.seg.common.messengers.Message;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class TokenHandler {

    public static Message handle(final TokenService tokenService, final Message request,
            final InstanceType instanceType, final BiFunction<String, Message, Message> callback) {
        return Optional.ofNullable((String) request.getValues().get("token"))
                .flatMap(token -> {
                    try {
                        final var identifier = tokenService.validateToken(token, instanceType);
                        final var response = callback.apply(identifier, request);

                        return Optional.of(response);
                    } catch (final JWTVerificationException e) {
                        return Optional.empty();
                    }
                })
                .orElseGet(() -> new Message(MessageType.UNAUTHORIZED)
                        .withValue("message", "Token inválido"));
    }

}
