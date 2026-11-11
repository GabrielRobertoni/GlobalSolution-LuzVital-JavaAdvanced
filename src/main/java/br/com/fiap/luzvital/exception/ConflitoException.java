package br.com.fiap.luzvital.exception;

/** HTTP 409: conflito com o estado atual (e-mail/CPF repetido, ocorrência já ativa ou já encerrada...). */
public class ConflitoException extends RuntimeException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
