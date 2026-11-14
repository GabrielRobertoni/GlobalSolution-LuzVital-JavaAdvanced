package br.com.fiap.luzvital.config;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import br.com.fiap.luzvital.security.ErroSegurancaHandler;
import br.com.fiap.luzvital.security.JwtService;
import br.com.fiap.luzvital.security.SenhaEncoder;
import br.com.fiap.luzvital.security.TokensRevogados;

/**
 * Spring Security stateless com JWT (resource server do próprio Spring Security).
 * Papéis: CUIDADOR e GESTOR. As regras por rota estão aqui; a regra "cuidador só mexe nos próprios
 * pacientes" fica nos serviços, que conferem o dono do registro.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private static final String[] ROTAS_PUBLICAS = {
            "/", "/auth/login", "/auth/cadastro",
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
            "/actuator/health", "/actuator/health/**", "/actuator/info", "/error"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ErroSegurancaHandler erroHandler,
                                                   JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // API stateless com Bearer token, sem cookie de sessão
                .cors(cors -> { })
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(ROTAS_PUBLICAS).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Painel do gestor
                        .requestMatchers("/ocorrencias/fila", "/ocorrencias/indicadores").hasRole("GESTOR")
                        .requestMatchers(HttpMethod.POST, "/ocorrencias/*/atendimentos").hasRole("GESTOR")
                        .requestMatchers("/usuarios/**").hasRole("GESTOR")
                        .requestMatchers(HttpMethod.POST, "/pontos-energia/**").hasRole("GESTOR")
                        .requestMatchers(HttpMethod.PUT, "/pontos-energia/**").hasRole("GESTOR")
                        .requestMatchers(HttpMethod.DELETE, "/pontos-energia/**").hasRole("GESTOR")
                        // Cadastro do cuidador
                        .requestMatchers(HttpMethod.POST, "/pacientes", "/equipamentos", "/equipamentos/leitura-etiqueta",
                                "/ocorrencias").hasRole("CUIDADOR")
                        .requestMatchers(HttpMethod.PUT, "/pacientes/**", "/equipamentos/**").hasRole("CUIDADOR")
                        .requestMatchers(HttpMethod.DELETE, "/pacientes/**", "/equipamentos/**").hasRole("CUIDADOR")
                        .requestMatchers(HttpMethod.POST, "/pacientes/*/plano-contingencia").hasRole("CUIDADOR")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(erroHandler)
                        .accessDeniedHandler(erroHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(erroHandler)
                        .accessDeniedHandler(erroHandler));
        return http.build();
    }

    /** BCrypt para senhas novas; aceita também as senhas fictícias sem hash da carga do banco da disciplina. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new SenhaEncoder();
    }

    /** Chave HS256. Sem JWT_SECRET, gera uma chave aleatória (os tokens deixam de valer quando a API reinicia). */
    @Bean
    public SecretKey jwtSecretKey(LuzVitalProperties props) {
        String segredo = props.jwt().segredo();
        byte[] bytes;
        if (segredo == null || segredo.isBlank()) {
            bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            log.warn("JWT_SECRET não definido: usando chave aleatória. Defina JWT_SECRET (32+ caracteres) para "
                    + "manter os tokens entre reinícios e para a API .NET validar o mesmo token.");
        } else {
            bytes = segredo.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32) {
                throw new IllegalStateException("JWT_SECRET precisa ter pelo menos 32 caracteres (HS256)");
            }
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey, LuzVitalProperties props, TokensRevogados revogados) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> audiencia = new JwtClaimValidator<List<String>>("aud",
                aud -> aud != null && aud.contains(props.jwt().audiencia()));
        OAuth2TokenValidator<Jwt> naoRevogado = jwt -> revogados.revogado(jwt.getId())
                ? OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token revogado (logout)", null))
                : OAuth2TokenValidatorResult.success();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(props.jwt().emissor()), audiencia, naoRevogado));
        return decoder;
    }

    /** Converte a claim "role" do token em ROLE_CUIDADOR / ROLE_GESTOR. */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter papeis = new JwtGrantedAuthoritiesConverter();
        papeis.setAuthoritiesClaimName(JwtService.CLAIM_PAPEL);
        papeis.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(papeis);
        return conversor;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(LuzVitalProperties props) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOriginPatterns(props.cors().origens());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        cors.setExposedHeaders(List.of("Location"));
        cors.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
