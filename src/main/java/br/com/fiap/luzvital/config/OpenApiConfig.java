package br.com.fiap.luzvital.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/** Swagger em /swagger-ui.html, com o botão Authorize para colar o token JWT. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI luzVitalOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Luz Vital API (Java)")
                        .version("1.0.0")
                        .description("""
                                Global Solution 2026/2 · CitizenTech. Cadastro de pacientes eletrodependentes e seus aparelhos, \
                                plano de contingência gerado por IA (Spring AI), botão "Estou sem luz" com fila do gestor ordenada \
                                pelos minutos de bateria restantes e evento energia.caiu no RabbitMQ.

                                Como testar: faça POST /auth/login com um usuário de teste, copie o token e clique em Authorize.
                                Usuários de teste: gestor@luzvital.com / Gestor@123 · cuidador@luzvital.com / Cuidador@123.

                                O Luz Vital não substitui o SAMU (192), os Bombeiros (193) nem a distribuidora e não dá orientação clínica."""))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
