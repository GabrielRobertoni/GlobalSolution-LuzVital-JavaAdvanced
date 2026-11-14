package br.com.fiap.luzvital;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import org.springframework.security.crypto.password.PasswordEncoder;

import br.com.fiap.luzvital.domain.Papel;
import br.com.fiap.luzvital.domain.Usuario;
import br.com.fiap.luzvital.messaging.RabbitConfig;
import br.com.fiap.luzvital.repository.UsuarioRepository;

/**
 * Testes de integração da API (H2 em memória; RabbitTemplate simulado para verificar a publicação do evento).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegracaoTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private PasswordEncoder encoder;

    private String cuidador;
    private String gestor;

    @BeforeEach
    void preparar() throws Exception {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        cuidador = cadastrar("cuid" + sufixo + "@teste.com");
        // O cadastro público só cria CUIDADOR: o gestor de teste é gravado direto no banco
        gestor = tokenGestor("gest" + sufixo + "@teste.com");
    }

    @Test
    void semTokenDevolve401NoFormatoPadrao() throws Exception {
        mvc.perform(get("/pacientes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.mensagem").exists())
                .andExpect(jsonPath("$.caminho").value("/pacientes"));
    }

    @Test
    void enderecoRaizAbreOSwaggerSemToken() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui.html"));
    }

    @Test
    void cuidadorNaoAcessaFilaDoGestor() throws Exception {
        mvc.perform(get("/ocorrencias/fila").header("Authorization", "Bearer " + cuidador))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mvc.perform(get("/ocorrencias/fila").header("Authorization", "Bearer " + gestor))
                .andExpect(status().isOk());
    }

    @Test
    void validacaoDoEquipamentoDevolveCamposInvalidos() throws Exception {
        long pacienteId = criarPaciente();
        mvc.perform(post("/equipamentos").header("Authorization", "Bearer " + cuidador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":%d,"tipoAparelho":"VENTILADOR_MECANICO","fabricante":"Philips",
                                 "modelo":"Trilogy Evo","potenciaWatts":0,"autonomiaMinutos":-1,"suporteVida":true}
                                """.formatted(pacienteId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[*].campo", hasItem("potenciaWatts")))
                .andExpect(jsonPath("$.campos[*].campo", hasItem("autonomiaMinutos")));
    }

    @Test
    void estouSemLuzPublicaEnergiaCaiuEEIdempotente() throws Exception {
        long pacienteId = criarPaciente();
        mvc.perform(post("/equipamentos").header("Authorization", "Bearer " + cuidador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":%d,"tipoAparelho":"CONCENTRADOR_OXIGENIO","fabricante":"Philips Respironics",
                                 "modelo":"EverFlo","potenciaWatts":350,"autonomiaMinutos":0,"suporteVida":true}
                                """.formatted(pacienteId)))
                .andExpect(status().isCreated());
        verify(rabbitTemplate, atLeastOnce()).convertAndSend(eq(RabbitConfig.EXCHANGE),
                eq(RabbitConfig.RK_PLANO_SOLICITADO), any(Object.class));

        String chave = UUID.randomUUID().toString();
        String corpo = "{\"pacienteId\":" + pacienteId + "}";
        String resposta = mvc.perform(post("/ocorrencias").header("Authorization", "Bearer " + cuidador)
                        .header("Idempotency-Key", chave).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.minutosRestantes").value(0))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(resposta, "$.id");

        verify(rabbitTemplate).convertAndSend(eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.RK_ENERGIA_CAIU), any(Object.class));

        mvc.perform(post("/ocorrencias").header("Authorization", "Bearer " + cuidador)
                        .header("Idempotency-Key", chave).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        mvc.perform(post("/ocorrencias").header("Authorization", "Bearer " + cuidador)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isConflict());
    }

    @Test
    void abrirOcorrenciaSemAparelhoDeSuporteAVidaDevolve422() throws Exception {
        long pacienteId = criarPaciente();
        mvc.perform(post("/ocorrencias").header("Authorization", "Bearer " + cuidador)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pacienteId\":" + pacienteId + "}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void cadastroPublicoNuncaCriaGestor() throws Exception {
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + cuidador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papel").value("CUIDADOR"));
    }

    private String cadastrar(String email) throws Exception {
        String resp = mvc.perform(post("/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Teste\",\"email\":\"" + email + "\",\"senha\":\"Senha123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(resp, "$.token");
    }

    private String tokenGestor(String email) throws Exception {
        usuarios.save(new Usuario("Gestor", email, encoder.encode("Gestor123"), Papel.GESTOR));
        String resp = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"Gestor123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(resp, "$.token");
    }

    private long criarPaciente() throws Exception {
        String cpf = cpfValido();
        String resp = mvc.perform(post("/pacientes").header("Authorization", "Bearer " + cuidador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Paciente Teste","cpf":"%s","endereco":"Rua A, 1","bairro":"Bela Vista",
                                 "cidade":"São Paulo","uf":"SP","cep":"01318-000","telefone":"(11) 98888-1111"}
                                """.formatted(cpf)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(resp, "$.id")).longValue();
    }

    /** Gera um CPF válido aleatório (dígitos verificadores corretos) para não colidir entre os testes. */
    private static String cpfValido() {
        int[] d = new int[11];
        java.util.Random r = new java.util.Random();
        for (int i = 0; i < 9; i++) {
            d[i] = r.nextInt(10);
        }
        d[0] = 1 + r.nextInt(9);
        for (int k = 9; k <= 10; k++) {
            int soma = 0;
            for (int i = 0; i < k; i++) {
                soma += d[i] * (k + 1 - i);
            }
            d[k] = (soma * 10) % 11 % 10;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            sb.append(d[i]);
            if (i == 2 || i == 5) {
                sb.append('.');
            } else if (i == 8) {
                sb.append('-');
            }
        }
        return sb.toString();
    }
}
