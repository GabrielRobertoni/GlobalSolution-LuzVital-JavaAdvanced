package br.com.fiap.luzvital.exception;

/** HTTP 422: a requisição é válida, mas a regra de negócio impede a operação. */
public class RegraNegocioException extends RuntimeException {

    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
