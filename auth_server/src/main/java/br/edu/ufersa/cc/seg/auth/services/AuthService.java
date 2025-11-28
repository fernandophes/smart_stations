package br.edu.ufersa.cc.seg.auth.services;

import br.edu.ufersa.cc.seg.auth.exceptions.AuthFailureException;
import br.edu.ufersa.cc.seg.common.auth.TokenService;

public class AuthService {

    private final InstanceService instanceService = new InstanceService();
    private final TokenService tokenService;

    public AuthService(final String jwtSecret) {
        this.tokenService = new TokenService(jwtSecret);
    }

    public String authenticate(final String identifier, final String instanceSecret) throws AuthFailureException {
        return instanceService.getByIdentifierAndSecret(identifier, instanceSecret)
                .map(instance -> tokenService.generateToken(identifier, instance.getType()))
                .orElseThrow(() -> new AuthFailureException("Falha na autenticação"));
    }

}
