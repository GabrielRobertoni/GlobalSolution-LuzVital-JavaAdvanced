package br.com.fiap.luzvital.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import br.com.fiap.luzvital.exception.ErroResposta;
import br.com.fiap.luzvital.exception.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * 401 e 403 gerados pelo filtro de segurança (antes de chegar no controller), no mesmo formato
 * {@link ErroResposta} do {@code @RestControllerAdvice}.
 */
@Component
public class ErroSegurancaHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    public ErroSegurancaHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        boolean enviouToken = request.getHeader("Authorization") != null;
        String mensagem = enviouToken
                ? "Token inválido, expirado ou revogado. Faça login novamente"
                : "Autenticação necessária: envie o cabeçalho Authorization: Bearer <token>";
        response.setHeader("WWW-Authenticate", "Bearer");
        escrever(response, HttpStatus.UNAUTHORIZED, mensagem, request.getRequestURI());
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        escrever(response, HttpStatus.FORBIDDEN, "Seu perfil não tem permissão para este recurso", request.getRequestURI());
    }

    private void escrever(HttpServletResponse response, HttpStatus status, String mensagem, String caminho)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), GlobalExceptionHandler.corpo(status, mensagem, caminho, null));
    }
}
