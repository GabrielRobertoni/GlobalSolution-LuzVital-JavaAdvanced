package br.com.fiap.luzvital.service;

import org.springframework.security.access.AccessDeniedException;

import br.com.fiap.luzvital.domain.Paciente;
import br.com.fiap.luzvital.security.UsuarioAutenticado;

/** Regra de posse: o CUIDADOR só vê e edita os próprios pacientes; o GESTOR vê todos. */
final class Acesso {

    private Acesso() {
    }

    static void leitura(Paciente paciente, UsuarioAutenticado usuario) {
        if (usuario.isGestor()) {
            return;
        }
        exigirDono(paciente, usuario);
    }

    static void escrita(Paciente paciente, UsuarioAutenticado usuario) {
        exigirDono(paciente, usuario);
    }

    private static void exigirDono(Paciente paciente, UsuarioAutenticado usuario) {
        if (!paciente.pertenceA(usuario.id())) {
            throw new AccessDeniedException("Este paciente pertence a outro cuidador");
        }
    }

    /** Filtro de listagem: cuidador vê só os seus (id dele); gestor vê todos (null). */
    static Long filtroCuidador(UsuarioAutenticado usuario) {
        return usuario.isGestor() ? null : usuario.id();
    }
}
