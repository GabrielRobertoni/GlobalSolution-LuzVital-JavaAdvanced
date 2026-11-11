package br.com.fiap.luzvital.security;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import br.com.fiap.luzvital.config.LuzVitalProperties;
import br.com.fiap.luzvital.domain.Usuario;

/**
 * Emite o JWT (HS256). Claims: sub = id do usuário, email, nome, role (CUIDADOR ou GESTOR), jti, iss, aud, iat, exp.
 * A API .NET valida o mesmo token com a mesma chave (JWT_SECRET), o mesmo emissor e a mesma audiência.
 */
@Service
public class JwtService {

    public static final String CLAIM_PAPEL = "role";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_NOME = "nome";

    private final JwtEncoder encoder;
    private final LuzVitalProperties.Jwt config;

    public JwtService(JwtEncoder encoder, LuzVitalProperties props) {
        this.encoder = encoder;
        this.config = props.jwt();
    }

    public TokenEmitido emitir(Usuario usuario) {
        Instant agora = Instant.now();
        Instant expira = agora.plus(config.expiracao());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(config.emissor())
                .audience(List.of(config.audiencia()))
                .subject(String.valueOf(usuario.getId()))
                .id(UUID.randomUUID().toString())
                .issuedAt(agora)
                .expiresAt(expira)
                .claim(CLAIM_EMAIL, usuario.getEmail())
                .claim(CLAIM_NOME, usuario.getNome())
                .claim(CLAIM_PAPEL, usuario.getPapel().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenEmitido(token, expira);
    }

    public record TokenEmitido(String valor, Instant expiraEm) {
    }
}
