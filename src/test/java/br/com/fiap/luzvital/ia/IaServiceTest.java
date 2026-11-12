package br.com.fiap.luzvital.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import br.com.fiap.luzvital.config.LuzVitalProperties;
import br.com.fiap.luzvital.domain.FonteReserva;
import br.com.fiap.luzvital.domain.TipoAparelho;
import br.com.fiap.luzvital.exception.IaIndisponivelException;

/** Testa a integração com o Spring AI (ChatClient + saída estruturada) usando um ChatModel falso. */
class IaServiceTest {

    private static final String RESPOSTA_LLM = """
            ```json
            {"resumo":"Ventilador com 15 h de bateria.",
             "etapas":[{"faixaTempo":"Primeiros 30 minutos","acoes":["Toque em Estou sem luz"]}],
             "contatos":[{"nome":"Condomínio","telefone":"(11) 3251-0000","quandoAcionar":"Recarga"}],
             "sinaisAlerta":["Falta de ar"],
             "preparacao":["Bateria carregada"]}
            ```""";

    private final ContextoPlano contexto = new ContextoPlano("Bela Vista", "São Paulo", "(11) 98888-1111",
            List.of(new ContextoPlano.Aparelho(TipoAparelho.VENTILADOR_MECANICO, "Philips", "Trilogy Evo",
                    new BigDecimal("120.00"), 900, true, FonteReserva.BATERIA_EXTERNA, new BigDecimal("8.3"))),
            List.of(new ContextoPlano.Ponto("Condomínio", "(11) 3251-0000", "Bela Vista", true)));

    @Test
    void geraPlanoEstruturadoEGaranteContatosDeEmergencia() {
        AtomicReference<Prompt> enviado = new AtomicReference<>();
        IaService ia = servico(prompt -> {
            enviado.set(prompt);
            return RESPOSTA_LLM;
        }, true, "chave");

        PlanoGerado plano = ia.gerarPlano(contexto);

        assertThat(plano.resumo()).contains("15 h");
        assertThat(plano.etapas()).hasSize(1);
        assertThat(plano.contatos()).extracting(PlanoGerado.Contato::telefone).contains("192", "193", "(11) 3251-0000");
        String prompt = enviado.get().getContents();
        assertThat(prompt).contains("Trilogy Evo").contains("Não dê orientação clínica").contains("faixaTempo");
    }

    @Test
    void planoIncompletoDaIaViraIndisponivel() {
        java.util.concurrent.atomic.AtomicInteger chamadas = new java.util.concurrent.atomic.AtomicInteger();
        IaService ia = servico(p -> {
            chamadas.incrementAndGet();
            return "{\"resumo\":\"x\",\"etapas\":[],\"contatos\":[],\"sinaisAlerta\":[]}";
        }, true, "chave");
        assertThatThrownBy(() -> ia.gerarPlano(contexto)).isInstanceOf(IaIndisponivelException.class);
        assertThat(chamadas.get()).isEqualTo(2); // tentou de novo uma vez
    }

    @Test
    void jsonCortadoNaPrimeiraRespostaTentaDeNovo() {
        java.util.concurrent.atomic.AtomicInteger chamadas = new java.util.concurrent.atomic.AtomicInteger();
        IaService ia = servico(p -> chamadas.incrementAndGet() == 1
                ? "{\"resumo\":\"Ventilador com 15 h\",\"etapas\":[{\"faixaTe" // resposta cortada no meio
                : RESPOSTA_LLM, true, "chave");
        PlanoGerado plano = ia.gerarPlano(contexto);
        assertThat(plano.resumo()).contains("15 h");
        assertThat(chamadas.get()).isEqualTo(2);
    }

    @Test
    void erroDeCotaNaoERepetido() {
        java.util.concurrent.atomic.AtomicInteger chamadas = new java.util.concurrent.atomic.AtomicInteger();
        IaService ia = servico(p -> {
            chamadas.incrementAndGet();
            throw new IllegalStateException("429: You exceeded your current quota");
        }, true, "chave");
        assertThatThrownBy(() -> ia.gerarPlano(contexto)).isInstanceOf(IaIndisponivelException.class);
        assertThat(chamadas.get()).isEqualTo(1);
    }

    @Test
    void semChaveAIaFicaIndisponivel() {
        IaService ia = servico(p -> RESPOSTA_LLM, true, "");
        assertThat(ia.disponivel()).isFalse();
        assertThatThrownBy(() -> ia.gerarPlano(contexto)).isInstanceOf(IaIndisponivelException.class);
    }

    @Test
    void planoPadraoDeConcentradorSemBateriaPrioriza_reservaEContatos() {
        ContextoPlano semBateria = new ContextoPlano("Bela Vista", "São Paulo", "(11) 98888-1111",
                List.of(new ContextoPlano.Aparelho(TipoAparelho.CONCENTRADOR_OXIGENIO, "Philips Respironics", "EverFlo",
                        new BigDecimal("350.00"), 0, true, FonteReserva.CILINDRO_OXIGENIO, new BigDecimal("2.9"))),
                List.of());
        PlanoGerado plano = PlanoPadrao.montar(semBateria);
        assertThat(plano.completo()).isTrue();
        assertThat(plano.resumo()).contains("sem bateria");
        assertThat(plano.contatos()).extracting(PlanoGerado.Contato::telefone).contains("192", "193", "199");
    }

    @Test
    void promptNaoLevaNomeNemCpfDoPaciente() {
        assertThat(contexto.descricaoParaPrompt()).doesNotContain("{").contains("Bela Vista").contains("900 min");
    }

    private IaService servico(java.util.function.Function<Prompt, String> resposta, boolean habilitada, String chave) {
        ChatModel modelo = prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage(resposta.apply(prompt)))));
        StaticListableBeanFactory bf = new StaticListableBeanFactory();
        bf.addBean("chatClientBuilder", ChatClient.builder(modelo));
        LuzVitalProperties props = new LuzVitalProperties(null, null,
                new LuzVitalProperties.Ia(habilitada, chave, "modelo-teste"), null);
        return new IaService(bf.getBeanProvider(ChatClient.Builder.class), props);
    }
}
