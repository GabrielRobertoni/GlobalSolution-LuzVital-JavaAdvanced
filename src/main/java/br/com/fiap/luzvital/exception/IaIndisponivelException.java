package br.com.fiap.luzvital.exception;

/** HTTP 503: o provedor de IA não está configurado ou não respondeu. */
public class IaIndisponivelException extends RuntimeException {

    public IaIndisponivelException(String mensagem) {
        super(mensagem);
    }

    public IaIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
