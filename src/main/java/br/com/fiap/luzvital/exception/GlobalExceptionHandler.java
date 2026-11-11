package br.com.fiap.luzvital.exception;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

/**
 * Tratamento global: toda exceção vira {@link ErroResposta}, com o mesmo formato em qualquer endpoint.
 * Os 401/403 que nascem no filtro de segurança (antes do controller) usam o mesmo formato em
 * {@code security.ErroSegurancaHandler}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResposta> validacao(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ErroResposta.CampoInvalido> campos = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            campos.add(new ErroResposta.CampoInvalido(fe.getField(), fe.getDefaultMessage()));
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> campos.add(new ErroResposta.CampoInvalido(ge.getObjectName(), ge.getDefaultMessage())));
        return responder(HttpStatus.BAD_REQUEST, "Dados inválidos", req, campos);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErroResposta> validacaoParametros(HandlerMethodValidationException ex, HttpServletRequest req) {
        List<ErroResposta.CampoInvalido> campos = new ArrayList<>();
        ex.getParameterValidationResults().forEach(r -> r.getResolvableErrors().forEach(err ->
                campos.add(new ErroResposta.CampoInvalido(r.getMethodParameter().getParameterName(),
                        err.getDefaultMessage()))));
        return responder(HttpStatus.BAD_REQUEST, "Parâmetros inválidos", req, campos);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErroResposta> violacao(ConstraintViolationException ex, HttpServletRequest req) {
        List<ErroResposta.CampoInvalido> campos = ex.getConstraintViolations().stream()
                .map(v -> new ErroResposta.CampoInvalido(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return responder(HttpStatus.BAD_REQUEST, "Parâmetros inválidos", req, campos);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResposta> corpoIlegivel(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return responder(HttpStatus.BAD_REQUEST,
                "Corpo da requisição inválido: verifique o JSON, os tipos dos campos e os valores dos enums", req, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErroResposta> tipoInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return responder(HttpStatus.BAD_REQUEST, "Valor inválido para o parâmetro '" + ex.getName() + "'", req, null);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
            MissingRequestHeaderException.class})
    public ResponseEntity<ErroResposta> faltando(Exception ex, HttpServletRequest req) {
        return responder(HttpStatus.BAD_REQUEST, ex.getMessage(), req, null);
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErroResposta> ordenacaoInvalida(PropertyReferenceException ex, HttpServletRequest req) {
        return responder(HttpStatus.BAD_REQUEST, "Campo de ordenação inválido: " + ex.getPropertyName(), req, null);
    }

    /** Ordenação por campo inexistente em consultas @Query (?sort=campoQueNaoExiste). */
    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    public ResponseEntity<ErroResposta> usoInvalido(InvalidDataAccessApiUsageException ex, HttpServletRequest req) {
        if (ex.getMostSpecificCause() instanceof org.hibernate.query.sqm.PathElementException
                || ex.getMostSpecificCause() instanceof org.hibernate.query.sqm.UnknownPathException) {
            return responder(HttpStatus.BAD_REQUEST, "Campo de ordenação ou filtro inválido", req, null);
        }
        return inesperado(ex, req);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErroResposta> naoAutenticado(AuthenticationException ex, HttpServletRequest req) {
        return responder(HttpStatus.UNAUTHORIZED, ex.getMessage(), req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErroResposta> acessoNegado(AccessDeniedException ex, HttpServletRequest req) {
        String msg = ex.getMessage() == null || ex.getMessage().startsWith("Access Denied")
                ? "Seu perfil não tem permissão para este recurso" : ex.getMessage();
        return responder(HttpStatus.FORBIDDEN, msg, req, null);
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResposta> naoEncontrado(RecursoNaoEncontradoException ex, HttpServletRequest req) {
        return responder(HttpStatus.NOT_FOUND, ex.getMessage(), req, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErroResposta> rotaInexistente(NoResourceFoundException ex, HttpServletRequest req) {
        return responder(HttpStatus.NOT_FOUND, "Recurso não encontrado", req, null);
    }

    @ExceptionHandler(ConflitoException.class)
    public ResponseEntity<ErroResposta> conflito(ConflitoException ex, HttpServletRequest req) {
        return responder(HttpStatus.CONFLICT, ex.getMessage(), req, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErroResposta> integridade(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Violação de integridade em {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return responder(HttpStatus.CONFLICT, "Operação viola uma restrição de integridade dos dados", req, null);
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<ErroResposta> regraNegocio(RegraNegocioException ex, HttpServletRequest req) {
        return responder(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage(), req, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErroResposta> metodo(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return responder(HttpStatus.METHOD_NOT_ALLOWED, "Método " + ex.getMethod() + " não suportado nesta rota", req, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErroResposta> midia(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        return responder(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type não suportado: " + ex.getContentType(), req, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErroResposta> arquivoGrande(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        return responder(HttpStatus.CONTENT_TOO_LARGE, "A foto deve ter no máximo 5 MB", req, null);
    }

    @ExceptionHandler(IaIndisponivelException.class)
    public ResponseEntity<ErroResposta> iaIndisponivel(IaIndisponivelException ex, HttpServletRequest req) {
        return responder(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResposta> inesperado(Exception ex, HttpServletRequest req) {
        log.error("Erro inesperado em {} {}", req.getMethod(), req.getRequestURI(), ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado", req, null);
    }

    public static ErroResposta corpo(HttpStatus status, String mensagem, String caminho,
                                     List<ErroResposta.CampoInvalido> campos) {
        return new ErroResposta(LocalDateTime.now(), status.value(), status.getReasonPhrase(), mensagem, caminho, campos);
    }

    private ResponseEntity<ErroResposta> responder(HttpStatus status, String mensagem, HttpServletRequest req,
                                                   List<ErroResposta.CampoInvalido> campos) {
        return ResponseEntity.status(status).body(corpo(status, mensagem, req.getRequestURI(), campos));
    }
}
