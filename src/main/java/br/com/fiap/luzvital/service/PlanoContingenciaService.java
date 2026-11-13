package br.com.fiap.luzvital.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.fiap.luzvital.domain.OrigemPlano;
import br.com.fiap.luzvital.domain.Paciente;
import br.com.fiap.luzvital.domain.PontoEnergia;
import br.com.fiap.luzvital.domain.StatusPlano;
import br.com.fiap.luzvital.dto.PlanoDtos;
import br.com.fiap.luzvital.dto.PlanoDtos.PlanoContingenciaResposta;
import br.com.fiap.luzvital.exception.IaIndisponivelException;
import br.com.fiap.luzvital.exception.RecursoNaoEncontradoException;
import br.com.fiap.luzvital.exception.RegraNegocioException;
import br.com.fiap.luzvital.ia.ContextoPlano;
import br.com.fiap.luzvital.ia.IaService;
import br.com.fiap.luzvital.ia.PlanoGerado;
import br.com.fiap.luzvital.ia.PlanoPadrao;
import br.com.fiap.luzvital.messaging.PlanoSolicitadoEvento;
import br.com.fiap.luzvital.repository.PacienteRepository;
import br.com.fiap.luzvital.repository.PontoEnergiaRepository;
import br.com.fiap.luzvital.security.UsuarioAutenticado;

/**
 * Plano de contingência (requisito 4, Spring AI). Fluxo:
 * <ol>
 *   <li>Salvar/alterar/excluir um aparelho (ou consultar um plano que ainda não existe) chama {@link #solicitar}:
 *       o plano fica GERANDO e o evento plano.solicitado é publicado no RabbitMQ após o commit.</li>
 *   <li>O consumidor chama {@link #gerar}: lê os aparelhos no banco, chama o LLM fora de transação e guarda o plano.</li>
 *   <li>O app consulta GET /pacientes/{id}/plano-contingencia e salva o plano no celular para uso offline.</li>
 * </ol>
 * O plano gerado fica em memória da API (o banco da disciplina é usado sem tabelas extras). Se a API reiniciar,
 * o plano é gerado de novo na próxima consulta.
 */
@Service
public class PlanoContingenciaService {

    private static final Logger log = LoggerFactory.getLogger(PlanoContingenciaService.class);
    private static final int MAX_PONTOS_NO_PLANO = 5;

    private final Map<Long, Registro> planos = new ConcurrentHashMap<>();

    private final PacienteRepository pacientes;
    private final PontoEnergiaRepository pontos;
    private final IaService ia;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate tx;

    public PlanoContingenciaService(PacienteRepository pacientes, PontoEnergiaRepository pontos, IaService ia,
                                    ApplicationEventPublisher eventos, PlatformTransactionManager txManager) {
        this.pacientes = pacientes;
        this.pontos = pontos;
        this.ia = ia;
        this.eventos = eventos;
        this.tx = new TransactionTemplate(txManager);
    }

    /** Marca o plano como GERANDO e publica plano.solicitado (após o commit da transação atual, se houver). */
    public void solicitar(Paciente paciente, String motivo) {
        if (paciente.getEquipamentos().isEmpty()) {
            planos.remove(paciente.getId()); // sem aparelhos não há plano
            return;
        }
        LocalDateTime agora = LocalDateTime.now();
        Registro anterior = planos.get(paciente.getId());
        planos.put(paciente.getId(), anterior == null
                ? Registro.gerando(agora)
                : anterior.comNovaSolicitacao(agora));
        eventos.publishEvent(new PlanoSolicitadoEvento(paciente.getId(), motivo, agora));
    }

    /** Chamado pelo consumidor da fila luzvital.plano-contingencia. */
    public void gerar(Long pacienteId) {
        ContextoPlano contexto = tx.execute(status -> ler(pacienteId));
        Registro registro = planos.get(pacienteId);
        if (contexto == null || registro == null) {
            log.info("Plano do paciente {} ignorado: paciente removido ou sem aparelhos", pacienteId);
            return;
        }
        LocalDateTime solicitadoEm = registro.dataSolicitacao();

        PlanoGerado plano;
        OrigemPlano origem;
        String modelo = null;
        String observacao = null;
        if (ia.disponivel()) {
            try {
                plano = ia.gerarPlano(contexto);
                origem = OrigemPlano.IA;
                modelo = ia.modelo();
            } catch (IaIndisponivelException ex) {
                plano = PlanoPadrao.montar(contexto);
                origem = OrigemPlano.REGRAS;
                observacao = "A IA não respondeu agora; foi usado o plano padrão. Gere de novo mais tarde.";
            }
        } else {
            plano = PlanoPadrao.montar(contexto);
            origem = OrigemPlano.REGRAS;
            observacao = "IA generativa não configurada nesta API (IA_API_KEY); foi usado o plano padrão por regras.";
        }

        Registro pronto = new Registro(StatusPlano.PRONTO, origem, plano, modelo, observacao, solicitadoEm,
                LocalDateTime.now());
        // Se os aparelhos mudaram de novo durante a geração, a solicitação mais nova gera o plano final
        planos.computeIfPresent(pacienteId, (id, atual) -> atual.dataSolicitacao().equals(solicitadoEm) ? pronto : atual);
        log.info("Plano do paciente {} gerado (origem {})", pacienteId, origem);
    }

    /** Consulta o plano; se ainda não existe (ex.: API reiniciada), pede a geração e devolve GERANDO. */
    @Transactional(readOnly = true)
    public PlanoContingenciaResposta consultar(Long pacienteId, UsuarioAutenticado usuario) {
        Paciente paciente = carregar(pacienteId);
        Acesso.leitura(paciente, usuario);
        if (paciente.getEquipamentos().isEmpty()) {
            throw new RecursoNaoEncontradoException("O paciente ainda não tem plano: cadastre ao menos um aparelho");
        }
        if (!planos.containsKey(pacienteId)) {
            solicitar(paciente, "primeira consulta");
        }
        return resposta(pacienteId, planos.get(pacienteId));
    }

    @Transactional(readOnly = true)
    public PlanoContingenciaResposta regenerar(Long pacienteId, UsuarioAutenticado usuario) {
        Paciente paciente = carregar(pacienteId);
        Acesso.escrita(paciente, usuario);
        if (paciente.getEquipamentos().isEmpty()) {
            throw new RegraNegocioException("Cadastre ao menos um aparelho para gerar o plano de contingência");
        }
        solicitar(paciente, "pedido do cuidador");
        return resposta(pacienteId, planos.get(pacienteId));
    }

    /** Paciente excluído: o plano dele deixa de existir. */
    public void descartar(Long pacienteId) {
        planos.remove(pacienteId);
    }

    /** Situação do plano para a listagem de pacientes (nulo se ainda não foi pedido). */
    public StatusPlano status(Long pacienteId) {
        Registro r = planos.get(pacienteId);
        return r == null ? null : r.status();
    }

    /** Planos parados em GERANDO há mais tempo que o limite (ex.: RabbitMQ estava fora do ar). */
    public List<Long> pendentesDesde(LocalDateTime limite) {
        return planos.entrySet().stream()
                .filter(e -> e.getValue().status() == StatusPlano.GERANDO && e.getValue().dataSolicitacao().isBefore(limite))
                .map(Map.Entry::getKey)
                .toList();
    }

    public LocalDateTime solicitadoEm(Long pacienteId) {
        Registro r = planos.get(pacienteId);
        return r == null ? null : r.dataSolicitacao();
    }

    private Paciente carregar(Long pacienteId) {
        return pacientes.findById(pacienteId).orElseThrow(() -> new RecursoNaoEncontradoException("Paciente", pacienteId));
    }

    private ContextoPlano ler(Long pacienteId) {
        Paciente paciente = pacientes.findById(pacienteId).orElse(null);
        if (paciente == null || paciente.getEquipamentos().isEmpty()) {
            return null;
        }
        List<PontoEnergia> pontosBairro = pontos.findByDisponivel(true).stream()
                .filter(p -> Formatos.mesmoBairro(p.getBairro(), paciente.getBairro()))
                .sorted((a, b) -> Boolean.compare(b.isPossuiGerador(), a.isPossuiGerador()))
                .limit(MAX_PONTOS_NO_PLANO)
                .toList();
        return ContextoPlano.de(paciente, pontosBairro);
    }

    private static PlanoContingenciaResposta resposta(Long pacienteId, Registro r) {
        return new PlanoContingenciaResposta(pacienteId, r.status(), r.origem(), r.modeloIa(), r.dataSolicitacao(),
                r.dataGeracao(), r.plano(), r.observacao(), PlanoDtos.AVISO_PLANO);
    }

    /** Plano guardado em memória; ao pedir de novo, o plano anterior continua visível até o novo ficar pronto. */
    record Registro(StatusPlano status, OrigemPlano origem, PlanoGerado plano, String modeloIa, String observacao,
                    LocalDateTime dataSolicitacao, LocalDateTime dataGeracao) {

        static Registro gerando(LocalDateTime agora) {
            return new Registro(StatusPlano.GERANDO, null, null, null, null, agora, null);
        }

        Registro comNovaSolicitacao(LocalDateTime agora) {
            return new Registro(StatusPlano.GERANDO, origem, plano, modeloIa, observacao, agora, dataGeracao);
        }
    }
}
