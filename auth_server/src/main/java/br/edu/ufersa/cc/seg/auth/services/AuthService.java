package br.edu.ufersa.cc.seg.auth.services;

import br.edu.ufersa.cc.seg.auth.exceptions.AuthFailureException;
import br.edu.ufersa.cc.seg.common.auth.TokenService;

public class AuthService {

    private final InstanceService instanceService = new InstanceService();
    private final TokenService tokenService;
    private final String secret;

    public AuthService() {
        this.secret = "";
        this.tokenService = new TokenService(secret);
    }

    public String authenticate(final String identifier, final String secret) throws AuthFailureException {
        return instanceService.getByIdentifierAndSecret(identifier, secret)
                .map(instance -> tokenService.generateToken(identifier, instance.getType()))
                .orElseThrow(() -> new AuthFailureException("Falha na autenticação"));
    }

}
