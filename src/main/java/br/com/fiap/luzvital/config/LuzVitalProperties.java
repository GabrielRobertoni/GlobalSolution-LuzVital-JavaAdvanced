package br.com.fiap.luzvital.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Configurações próprias da aplicação (prefixo luzvital no application.yml). */
@ConfigurationProperties(prefix = "luzvital")
public record LuzVitalProperties(
        Jwt jwt,
        Cors cors,
        Ia ia,
        Seed seed) {

    /**
     * @param segredo   chave HMAC (HS256) compartilhada com a API .NET; vazio = chave aleatória a cada subida
     * @param expiracao validade do token
     * @param emissor   claim iss
     * @param audiencia claim aud
     */
    public record Jwt(String segredo,
                      @DefaultValue("8h") Duration expiracao,
                      @DefaultValue("luz-vital-api") String emissor,
                      @DefaultValue("luz-vital-app") String audiencia) {
    }

    public record Cors(@DefaultValue("*") List<String> origens) {
    }

    /**
     * @param habilitada liga a IA generativa; desligada (ou sem chave), o plano usa as regras padrão
     * @param chave      chave do provedor (a mesma de spring.ai.openai.api-key)
     * @param modelo     nome do modelo, só para registro no plano gerado
     */
    public record Ia(@DefaultValue("true") boolean habilitada,
                     String chave,
                     String modelo) {
    }

    public record Seed(@DefaultValue("true") boolean habilitado) {
    }
}
