package br.com.fiap.luzvital.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.fiap.luzvital.domain.OrigemPlano;
import br.com.fiap.luzvital.domain.Paciente;
import br.com.fiap.luzvital.domain.PlanoContingencia;
import br.com.fiap.luzvital.domain.PontoEnergia;
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
import br.com.fiap.luzvital.repository.PlanoContingenciaRepository;
import br.com.fiap.luzvital.repository.PontoEnergiaRepository;
import br.com.fiap.luzvital.security.UsuarioAutenticado;
import tools.jackson.databind.json.JsonMapper;

/**
 * Plano de contingência (requisito 4, Spring AI). Fluxo:
 * <ol>
 *   <li>Salvar/alterar/excluir um aparelho chama {@link #solicitar}: o plano fica GERANDO e o evento
 *       plano.solicitado é publicado no RabbitMQ após o commit.</li>
 *   <li>O consumidor chama {@link #gerar}: lê os dados, chama o LLM fora de transação e grava o plano PRONTO.</li>
 *   <li>O app consulta GET /pacientes/{id}/plano-contingencia e guarda o plano para uso offline.</li>
 * </ol>
 */
@Service
public class PlanoContingenciaService {

    private static final Logger log = LoggerFactory.getLogger(PlanoContingenciaService.class);
    private static final int MAX_PONTOS_NO_PLANO = 5;

    private final PlanoContingenciaRepository planos;
    private final PacienteRepository pacientes;
    private final PontoEnergiaRepository pontos;
    private final IaService ia;
    private final ApplicationEventPublisher eventos;
    private final JsonMapper json;
    private final TransactionTemplate tx;

    public PlanoContingenciaService(PlanoContingenciaRepository planos, PacienteRepository pacientes,
                                    PontoEnergiaRepository pontos, IaService ia, ApplicationEventPublisher eventos,
                                    JsonMapper json, PlatformTransactionManager txManager) {
        this.planos = planos;
        this.pacientes = pacientes;
        this.pontos = pontos;
        this.ia = ia;
        this.eventos = eventos;
        this.json = json;
        this.tx = new TransactionTemplate(txManager);
    }

    /** Marca o plano como GERANDO e publica plano.solicitado (após o commit da transação atual). */
    @Transactional
    public void solicitar(Paciente paciente, String motivo) {
        if (paciente.getEquipamentos().isEmpty()) {
            paciente.setPlano(null); // sem aparelhos não há plano (orphanRemoval apaga o anterior)
            return;
        }
        PlanoContingencia plano = paciente.getPlano();
        if (plano == null) {
            plano = new PlanoContingencia(paciente);
            paciente.setPlano(plano);
        }
        LocalDateTime agora = LocalDateTime.now();
        plano.marcarGerando(agora);
        planos.save(plano);
        eventos.publishEvent(new PlanoSolicitadoEvento(paciente.getId(), motivo, agora));
    }

    /** Chamado pelo consumidor da fila luzvital.plano-contingencia. */
    public void gerar(Long pacienteId) {
        Leitura leitura = tx.execute(status -> ler(pacienteId));
        if (leitura == null) {
            log.info("Plano do paciente {} ignorado: paciente removido ou sem aparelhos", pacienteId);
            return;
        }

        PlanoGerado plano;
        OrigemPlano origem;
        String modelo = null;
        String observacao = null;
        if (ia.disponivel()) {
            try {
                plano = ia.gerarPlano(leitura.contexto());
                origem = OrigemPlano.IA;
                modelo = ia.modelo();
            } catch (IaIndisponivelException ex) {
                plano = PlanoPadrao.montar(leitura.contexto());
                origem = OrigemPlano.REGRAS;
                observacao = "A IA não respondeu agora; foi usado o plano padrão. Gere de novo mais tarde.";
            }
        } else {
            plano = PlanoPadrao.montar(leitura.contexto());
            origem = OrigemPlano.REGRAS;
            observacao = "IA generativa não configurada nesta API (IA_API_KEY); foi usado o plano padrão por regras.";
        }

        String conteudo = json.writeValueAsString(plano);
        OrigemPlano origemFinal = origem;
        String modeloFinal = modelo;
        String observacaoFinal = observacao;
        tx.executeWithoutResult(status -> planos.findByPacienteId(pacienteId).ifPresent(p -> {
            // Se o cadastro mudou de novo durante a geração, a solicitação mais nova gera o plano final
            if (p.getDataSolicitacao().equals(leitura.solicitadoEm())) {
                p.concluir(conteudo, origemFinal, modeloFinal, observacaoFinal, LocalDateTime.now());
            }
        }));
        log.info("Plano do paciente {} gerado (origem {})", pacienteId, origem);
    }

    @Transactional(readOnly = true)
    public PlanoContingenciaResposta consultar(Long pacienteId, UsuarioAutenticado usuario) {
        Paciente paciente = pacientes.findById(pacienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Paciente", pacienteId));
        Acesso.leitura(paciente, usuario);
        PlanoContingencia plano = planos.findByPacienteId(pacienteId).orElseThrow(() ->
                new RecursoNaoEncontradoException("O paciente ainda não tem plano: cadastre ao menos um aparelho"));
        return resposta(plano);
    }

    @Transactional
    public PlanoContingenciaResposta regenerar(Long pacienteId, UsuarioAutenticado usuario) {
        Paciente paciente = pacientes.findById(pacienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Paciente", pacienteId));
        Acesso.escrita(paciente, usuario);
        if (paciente.getEquipamentos().isEmpty()) {
            throw new RegraNegocioException("Cadastre ao menos um aparelho para gerar o plano de contingência");
        }
        solicitar(paciente, "pedido do cuidador");
        return resposta(paciente.getPlano());
    }

    private Leitura ler(Long pacienteId) {
        Paciente paciente = pacientes.findById(pacienteId).orElse(null);
        if (paciente == null || paciente.getEquipamentos().isEmpty() || paciente.getPlano() == null) {
            return null;
        }
        List<PontoEnergia> pontosBairro = pontos.findByDisponivel(true).stream()
                .filter(p -> Formatos.mesmoBairro(p.getBairro(), paciente.getBairro()))
                .sorted((a, b) -> Boolean.compare(b.isPossuiGerador(), a.isPossuiGerador()))
                .limit(MAX_PONTOS_NO_PLANO)
                .toList();
        return new Leitura(ContextoPlano.de(paciente, pontosBairro), paciente.getPlano().getDataSolicitacao());
    }

    private PlanoContingenciaResposta resposta(PlanoContingencia p) {
        PlanoGerado conteudo = p.getConteudo() == null ? null : json.readValue(p.getConteudo(), PlanoGerado.class);
        return new PlanoContingenciaResposta(p.getPaciente().getId(), p.getStatus(), p.getOrigem(), p.getModeloIa(),
                p.getDataSolicitacao(), p.getDataGeracao(), conteudo, p.getMensagemErro(), PlanoDtos.AVISO_PLANO);
    }

    private record Leitura(ContextoPlano contexto, LocalDateTime solicitadoEm) {
    }
}
