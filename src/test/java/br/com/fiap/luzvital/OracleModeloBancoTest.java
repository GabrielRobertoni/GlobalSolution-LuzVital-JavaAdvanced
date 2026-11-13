package br.com.fiap.luzvital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import br.com.fiap.luzvital.service.PlanoContingenciaService;

/**
 * Roda a API sobre o modelo e a carga ORIGINAIS do script da disciplina de banco (GSBancoOracle.sql),
 * num H2 em modo Oracle, com ddl-auto=none: o Hibernate não cria nem altera nenhuma tabela T_GS_*.
 * Nenhuma tabela extra: a API trabalha só com as 7 tabelas do script e não grava nada ao subir.
 * Os usuários entram com a senha que está gravada no script ('hash_carlos123', 'hash_luciana_adm'...).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:gsoracle;MODE=Oracle;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:oracle/gs-banco-modelo-e-carga.sql",
        "luzvital.seed.habilitado=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OracleModeloBancoTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlanoContingenciaService planoService;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Test
    @Order(0)
    void aoSubirAApiNaoGravaNemAlteraNadaNoBanco() {
        for (String tabela : List.of("T_GS_USUARIO", "T_GS_PACIENTE", "T_GS_EQUIPAMENTO", "T_GS_PONTO_ENERGIA",
                "T_GS_OCORRENCIA", "T_GS_ATENDIMENTO")) {
            assertThat(jdbc.queryForObject("select count(*) from " + tabela, Integer.class)).as(tabela).isEqualTo(5);
        }
        assertThat(jdbc.queryForList("select vl_score_prioridade from T_GS_OCORRENCIA order by id_ocorrencia",
                Integer.class)).containsExactly(1200, 1000, 700, 300, 100);
        assertThat(jdbc.queryForObject("select count(*) from T_GS_AUDITORIA_DML", Integer.class)).isZero();
        // Só as tabelas do script existem (nenhuma tabela de apoio criada pela API)
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'PUBLIC'", Integer.class))
                .isEqualTo(7);
    }

    @Test
    @Order(1)
    void usuariosDaCargaFazemLoginEVeemSeusPacientes() throws Exception {
        String carlos = login("carlos.silva@gmail.com", "hash_carlos123");
        mvc.perform(get("/pacientes").header("Authorization", "Bearer " + carlos))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].nome").value("João Carlos Silva"))
                .andExpect(jsonPath("$.conteudo[0].endereco").value("Rua das Flores, 105"))
                .andExpect(jsonPath("$.conteudo[0].equipamentos[0].modelo").value("Trilogy Evo"));

        String luciana = login("luciana.mendes@defesacivil.sp.gov.br", "hash_luciana_adm");
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + luciana))
                .andExpect(jsonPath("$.papel").value("GESTOR"));
        // A senha errada continua recusada e as senhas do script não foram alteradas
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"carlos.silva@gmail.com\",\"senha\":\"hash_carlos12\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select ds_senha from T_GS_USUARIO where ds_email = 'carlos.silva@gmail.com'",
                String.class)).isEqualTo("hash_carlos123");
        assertThat(jdbc.queryForObject("select count(*) from T_GS_USUARIO where ds_senha like 'hash_%'", Integer.class))
                .isEqualTo(5);
    }

    @Test
    @Order(2)
    void filaDoGestorLeAsOcorrenciasDaCargaComAparelhoAcentuado() throws Exception {
        String gestor = login("fernando.souza@enel.com.br", "hash_enel_gestor");
        String fila = mvc.perform(get("/ocorrencias/fila").header("Authorization", "Bearer " + gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andReturn().getResponse().getContentAsString();
        List<String> nomes = JsonPath.read(fila, "$.conteudo[*].pacienteNome");
        // Maria (concentrador, 0 min) e Ana (aspirador, 60 min abertos há 1 h = 0 min) antes de José (450 min)
        assertThat(nomes).containsExactly("Maria Aparecida Rocha", "Ana Beatriz Mendes", "José Eduardo Souza");
        List<String> tipos = JsonPath.read(fila, "$.conteudo[*].aparelhoCritico.tipoAparelho");
        assertThat(tipos).contains("ASPIRADOR_SECRECAO"); // gravado como 'ASPIRADOR_SECREÇÃO' na carga

        mvc.perform(get("/ocorrencias?status=EM_ABERTO&size=50").header("Authorization", "Bearer " + gestor))
                .andExpect(jsonPath("$.conteudo[*].atendimentos[*].tipoSocorro", hasItem("REMOCAO_UTI")));

        // O filtro por tipo encontra o aparelho gravado com acento pela carga do banco
        mvc.perform(get("/equipamentos?tipo=ASPIRADOR_SECRECAO").header("Authorization", "Bearer " + gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].modelo").value("1004-SUCTION"));
    }

    @Test
    @Order(3)
    void atendimentoEBateriaGravamDentroDasConstraintsDoBanco() throws Exception {
        String gestor = login("luciana.mendes@defesacivil.sp.gov.br", "hash_luciana_adm");
        Long jose = jdbc.queryForObject("""
                select o.id_ocorrencia from T_GS_OCORRENCIA o join T_GS_PACIENTE p on p.id_paciente = o.id_paciente
                where p.nr_cpf = '345.678.901-05'""", Long.class);

        mvc.perform(post("/ocorrencias/" + jose + "/atendimentos").header("Authorization", "Bearer " + gestor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoSocorro\":\"RELIGACAO_PRIORITARIA\",\"descricao\":\"Religação solicitada à Enel\"}"))
                .andExpect(status().isCreated());
        // CK_T_GS_ATEND_TIPO exige o valor com acento
        assertThat(jdbc.queryForObject("select count(*) from T_GS_ATENDIMENTO where tp_socorro = 'RELIGAÇÃO_PRIORITARIA'",
                Integer.class)).isEqualTo(2);

        mvc.perform(patch("/ocorrencias/" + jose + "/bateria").header("Authorization", "Bearer " + gestor)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"minutosRestantes\":45}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minutosRestantes").value(45))
                .andExpect(jsonPath("$.prioridadeScore").value(1015));
        assertThat(jdbc.queryForObject("select qt_bateria_min from T_GS_OCORRENCIA where id_ocorrencia = ?",
                Integer.class, jose)).isEqualTo(45);

        mvc.perform(post("/ocorrencias/" + jose + "/encerramento").header("Authorization", "Bearer " + gestor)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"resultado\":\"Energia restabelecida\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ATENDIDO"));
        mvc.perform(get("/ocorrencias/indicadores").header("Authorization", "Bearer " + gestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ocorrenciasAtendidas").value(2));
    }

    @Test
    @Order(4)
    void cuidadorAbreOcorrenciaCadastraPacienteEGeraPlano() throws Exception {
        String carlos = login("carlos.silva@gmail.com", "hash_carlos123");
        Long joao = jdbc.queryForObject("select id_paciente from T_GS_PACIENTE where nr_cpf = '123.456.789-01'", Long.class);
        String chave = UUID.randomUUID().toString();
        String corpo = "{\"pacienteId\":" + joao + ",\"observacao\":\"Apagão no Jardim Paulista\"}";
        String resp = mvc.perform(post("/ocorrencias").header("Authorization", "Bearer " + carlos)
                        .header("Idempotency-Key", chave).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.minutosRestantes").value(900))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(resp, "$.id");
        mvc.perform(post("/ocorrencias").header("Authorization", "Bearer " + carlos)
                        .header("Idempotency-Key", chave).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        assertThat(jdbc.queryForObject("select count(*) from T_GS_OCORRENCIA where id_paciente = ?", Integer.class, joao))
                .isEqualTo(2); // a do script (ATENDIDO) + a nova, sem duplicar no reenvio

        String paciente = mvc.perform(post("/pacientes").header("Authorization", "Bearer " + carlos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Helena Costa","cpf":"111.222.333-44","endereco":"Rua Frei Caneca, 300",
                                 "bairro":"Consolação","cidade":"São Paulo","uf":"SP","cep":"01307-000","telefone":"(11) 95555-1234"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer pacienteId = JsonPath.read(paciente, "$.id");
        mvc.perform(post("/equipamentos").header("Authorization", "Bearer " + carlos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":%d,"tipoAparelho":"ASPIRADOR_SECRECAO","fabricante":"Nevoni","modelo":"1004-SUCTION",
                                 "potenciaWatts":80,"autonomiaMinutos":60,"suporteVida":true,"fonteReserva":"NENHUMA"}
                                """.formatted(pacienteId)))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select fl_suporte_vida from T_GS_EQUIPAMENTO where id_paciente = ?",
                String.class, pacienteId)).isEqualTo("S");
        assertThat(jdbc.queryForObject("select tp_aparelho from T_GS_EQUIPAMENTO where id_paciente = ?",
                String.class, pacienteId)).isEqualTo("ASPIRADOR_SECREÇÃO");
        mvc.perform(get("/equipamentos?tipo=ASPIRADOR_SECRECAO").header("Authorization", "Bearer " + carlos))
                .andExpect(jsonPath("$.totalElementos").value(1)); // o novo, do Carlos (o da carga é de outra cuidadora)

        mvc.perform(get("/pacientes/" + pacienteId + "/plano-contingencia").header("Authorization", "Bearer " + carlos))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GERANDO"));
        planoService.gerar(pacienteId.longValue()); // o que o consumidor do RabbitMQ faria
        mvc.perform(get("/pacientes/" + pacienteId + "/plano-contingencia").header("Authorization", "Bearer " + carlos))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PRONTO"))
                .andExpect(jsonPath("$.plano.etapas").isNotEmpty());

        mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + carlos)).andExpect(status().isNoContent());
        mvc.perform(get("/pacientes").header("Authorization", "Bearer " + carlos)).andExpect(status().isUnauthorized());
    }

    private String login(String email, String senha) throws Exception {
        String resp = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(resp, "$.token");
    }
}
