package br.com.fiap.luzvital.ia;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

import br.com.fiap.luzvital.config.LuzVitalProperties;
import br.com.fiap.luzvital.exception.IaIndisponivelException;

/**
 * Integração com o LLM via Spring AI (ChatClient + saída estruturada em records Java).
 * O provedor é qualquer API compatível com OpenAI (Gemini, OpenAI, Groq...), configurado por variáveis de ambiente.
 */
@Service
public class IaService {

    private static final Logger log = LoggerFactory.getLogger(IaService.class);

    static final String SISTEMA_PLANO = """
            Você é o assistente do app Luz Vital, que prepara famílias de pacientes eletrodependentes
            (que dependem de aparelhos elétricos para viver) para quedas de energia.
            Escreva em português do Brasil, com frases curtas e linguagem simples, para cuidadores que podem ser idosos.
            Regras obrigatórias:
            1. Não dê orientação clínica: não sugira mudar parâmetros do aparelho, fluxo de oxigênio, dose ou medicação.
               Para dúvidas clínicas, oriente procurar a equipe de saúde que acompanha o paciente.
            2. Use somente os telefones fornecidos nos dados. Nunca invente números, nomes ou endereços.
            3. Baseie os tempos nas autonomias informadas. Aparelho de suporte à vida sem bateria (autonomia 0) para
               assim que a luz cai: a primeira ação é usar a fonte reserva e pedir ajuda imediatamente.
            4. Inclua o SAMU 192 e os Bombeiros 193 nos contatos e diga para ligar 192 diante de qualquer sinal de alerta.
            5. Seja específico para os aparelhos listados e cite o botão "Estou sem luz" do app como forma de avisar o gestor.
            6. Monte de 3 a 5 etapas por faixa de tempo, de 2 a 4 ações por etapa, de 3 a 6 sinais de alerta e de 3 a 6 itens de preparação.
            """;

    static final String SISTEMA_ETIQUETA = """
            Você lê etiquetas de identificação de aparelhos elétricos de uso médico doméstico.
            Extraia apenas o que está escrito na imagem. Não invente nem complete valores.
            Se a imagem estiver ilegível ou não for uma etiqueta de aparelho, responda legivel=false e deixe os campos nulos.
            """;

    private final ObjectProvider<ChatClient.Builder> chatClientBuilder;
    private final LuzVitalProperties.Ia config;

    public IaService(ObjectProvider<ChatClient.Builder> chatClientBuilder, LuzVitalProperties props) {
        this.chatClientBuilder = chatClientBuilder;
        this.config = props.ia();
    }

    /** IA ligada, com chave configurada e com um ChatModel disponível no contexto. */
    public boolean disponivel() {
        return config.habilitada() && config.chave() != null && !config.chave().isBlank()
                && chatClientBuilder.getIfAvailable() != null;
    }

    public String modelo() {
        return config.modelo();
    }

    public PlanoGerado gerarPlano(ContextoPlano contexto) {
        ChatClient client = cliente();
        try {
            PlanoGerado plano = client.prompt()
                    .system(SISTEMA_PLANO)
                    .user("Monte o plano de contingência para esta família.\n" + contexto.descricaoParaPrompt())
                    .call()
                    .entity(PlanoGerado.class);
            if (plano == null || !plano.completo()) {
                throw new IaIndisponivelException("A IA devolveu um plano incompleto");
            }
            return garantirContatosOficiais(plano);
        } catch (IaIndisponivelException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Falha ao gerar plano com a IA: {}", ex.getMessage());
            throw new IaIndisponivelException("O provedor de IA não respondeu: " + ex.getMessage(), ex);
        }
    }

    public EtiquetaExtraida lerEtiqueta(byte[] imagem, MimeType tipo) {
        ChatClient client = cliente();
        try {
            return client.prompt()
                    .system(SISTEMA_ETIQUETA)
                    .user(u -> u.text("Leia a etiqueta deste aparelho e extraia os dados pedidos.")
                            .media(tipo, new ByteArrayResource(imagem)))
                    .call()
                    .entity(EtiquetaExtraida.class);
        } catch (RuntimeException ex) {
            log.warn("Falha ao ler etiqueta com a IA: {}", ex.getMessage());
            throw new IaIndisponivelException("Não foi possível ler a etiqueta agora. Preencha os dados manualmente.", ex);
        }
    }

    private ChatClient cliente() {
        if (!disponivel()) {
            throw new IaIndisponivelException(
                    "IA generativa não configurada nesta API (defina IA_API_KEY). Preencha os dados manualmente.");
        }
        return chatClientBuilder.getObject().build();
    }

    /** Guarda-corpo: os números de emergência entram no plano mesmo que o modelo os omita. */
    static PlanoGerado garantirContatosOficiais(PlanoGerado plano) {
        List<PlanoGerado.Contato> contatos = new ArrayList<>(plano.contatos());
        if (contatos.stream().noneMatch(c -> c.telefone() != null && c.telefone().contains("192"))) {
            contatos.add(new PlanoGerado.Contato("SAMU", "192", "Qualquer sinal de alerta no paciente"));
        }
        if (contatos.stream().noneMatch(c -> c.telefone() != null && c.telefone().contains("193"))) {
            contatos.add(new PlanoGerado.Contato("Corpo de Bombeiros", "193", "Risco de incêndio, choque ou remoção urgente"));
        }
        return new PlanoGerado(plano.resumo(), plano.etapas(), contatos, plano.sinaisAlerta(), plano.preparacao());
    }
}
