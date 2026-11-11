package br.com.fiap.luzvital.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import br.com.fiap.luzvital.domain.Papel;

/** Lê o usuário autenticado do contexto de segurança (claims sub, email e role do JWT). */
@Component
public class UsuarioAtual {

    public UsuarioAutenticado obter() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new AuthenticationCredentialsNotFoundException("Token de acesso ausente ou inválido");
        }
        return new UsuarioAutenticado(
                Long.valueOf(jwt.getSubject()),
                jwt.getClaimAsString(JwtService.CLAIM_EMAIL),
                Papel.valueOf(jwt.getClaimAsString(JwtService.CLAIM_PAPEL)));
    }
}
