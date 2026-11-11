package br.com.fiap.luzvital.security;

import br.com.fiap.luzvital.domain.Papel;

/** Quem está chamando a API, extraído do token JWT. */
public record UsuarioAutenticado(Long id, String email, Papel papel) {

    public boolean isGestor() {
        return papel == Papel.GESTOR;
    }

    public boolean isCuidador() {
        return papel == Papel.CUIDADOR;
    }
}
