package br.com.fiap.luzvital.exception;

/** HTTP 404. */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String recurso, Long id) {
        super(recurso + " " + id + " não encontrado(a)");
    }

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
