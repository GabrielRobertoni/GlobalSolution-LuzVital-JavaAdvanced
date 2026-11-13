package br.com.fiap.luzvital.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Tokens JWT invalidados no logout (pelo jti), guardados em memória até a data em que expirariam.
 * Não usa tabela: o banco da disciplina é usado sem alterações.
 */
@Component
public class TokensRevogados {

    private final Map<String, Instant> revogados = new ConcurrentHashMap<>();

    public void revogar(String jti, Instant expiraEm) {
        if (jti != null) {
            revogados.put(jti, expiraEm);
        }
    }

    public boolean revogado(String jti) {
        if (jti == null) {
            return false;
        }
        Instant expira = revogados.get(jti);
        return expira != null && expira.isAfter(Instant.now());
    }

    public int removerExpirados() {
        int antes = revogados.size();
        revogados.values().removeIf(expira -> !expira.isAfter(Instant.now()));
        return antes - revogados.size();
    }
}
