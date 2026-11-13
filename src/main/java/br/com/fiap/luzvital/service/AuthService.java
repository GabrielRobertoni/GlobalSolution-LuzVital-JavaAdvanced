package br.com.fiap.luzvital.service;

import java.time.Instant;
import java.util.Locale;

import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.fiap.luzvital.domain.Papel;
import br.com.fiap.luzvital.domain.Usuario;
import br.com.fiap.luzvital.dto.AuthDtos.AuthResposta;
import br.com.fiap.luzvital.dto.AuthDtos.CadastroUsuarioRequest;
import br.com.fiap.luzvital.dto.AuthDtos.LoginRequest;
import br.com.fiap.luzvital.dto.AuthDtos.UsuarioResposta;
import br.com.fiap.luzvital.dto.PaginaResposta;
import br.com.fiap.luzvital.exception.ConflitoException;
import br.com.fiap.luzvital.exception.RecursoNaoEncontradoException;
import br.com.fiap.luzvital.security.TokensRevogados;
import br.com.fiap.luzvital.repository.UsuarioRepository;
import br.com.fiap.luzvital.security.JwtService;

@Service
public class AuthService {

    private final UsuarioRepository usuarios;
    private final TokensRevogados revogados;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /** Hash usado quando o e-mail não existe, para o login levar o mesmo tempo nos dois casos. */
    private final String hashFicticio;

    public AuthService(UsuarioRepository usuarios, TokensRevogados revogados, PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.usuarios = usuarios;
        this.revogados = revogados;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.hashFicticio = passwordEncoder.encode(java.util.UUID.randomUUID().toString());
    }

    /** Cadastro público pelo app: sempre com o papel CUIDADOR (ninguém se cadastra como gestor sozinho). */
    @Transactional
    public AuthResposta cadastrarCuidador(CadastroUsuarioRequest req) {
        Usuario usuario = criar(req, Papel.CUIDADOR);
        return autenticado(usuario);
    }

    /** Só um GESTOR autenticado cria outro GESTOR. */
    @Transactional
    public UsuarioResposta cadastrarGestor(CadastroUsuarioRequest req) {
        return UsuarioResposta.de(criar(req, Papel.GESTOR));
    }

    @Transactional(readOnly = true)
    public AuthResposta login(LoginRequest req) {
        Usuario usuario = usuarios.findByEmailIgnoreCase(req.email().trim()).orElse(null);
        boolean senhaOk = passwordEncoder.matches(req.senha(), usuario == null ? hashFicticio : usuario.getSenhaHash());
        if (usuario == null || !senhaOk) {
            throw new BadCredentialsException("E-mail ou senha inválidos");
        }
        if (!usuario.isAtivo()) {
            throw new DisabledException("Usuário inativo (st_usuario = 'N')");
        }
        return autenticado(usuario);
    }

    /** Logout: o jti do token fica revogado até a data de expiração original. */
    public void logout(Jwt jwt) {
        Instant expira = jwt.getExpiresAt() == null ? Instant.now().plusSeconds(86400) : jwt.getExpiresAt();
        revogados.revogar(jwt.getId(), expira);
    }

    @Transactional(readOnly = true)
    public UsuarioResposta buscar(Long id) {
        return usuarios.findById(id).map(UsuarioResposta::de)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário", id));
    }

    @Transactional(readOnly = true)
    public PaginaResposta<UsuarioResposta> listar(Papel papel, Pageable pageable) {
        var pagina = papel == null ? usuarios.findAll(pageable) : usuarios.findByPapel(papel, pageable);
        return PaginaResposta.de(pagina, UsuarioResposta::de);
    }

    private Usuario criar(CadastroUsuarioRequest req, Papel papel) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (usuarios.existsByEmailIgnoreCase(email)) {
            throw new ConflitoException("Já existe um usuário com o e-mail " + email);
        }
        Usuario usuario = new Usuario(req.nome().trim(), email, passwordEncoder.encode(req.senha()), papel);
        return usuarios.save(usuario);
    }

    private AuthResposta autenticado(Usuario usuario) {
        JwtService.TokenEmitido token = jwtService.emitir(usuario);
        return new AuthResposta(token.valor(), "Bearer", token.expiraEm(), UsuarioResposta.de(usuario));
    }
}
