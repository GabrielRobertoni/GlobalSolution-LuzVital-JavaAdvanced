package br.com.fiap.luzvital.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.fiap.luzvital.domain.Atendimento;
import br.com.fiap.luzvital.domain.Equipamento;
import br.com.fiap.luzvital.domain.Ocorrencia;
import br.com.fiap.luzvital.domain.Paciente;
import br.com.fiap.luzvital.domain.PontoEnergia;
import br.com.fiap.luzvital.domain.StatusOcorrencia;
import br.com.fiap.luzvital.dto.OcorrenciaDtos;
import br.com.fiap.luzvital.dto.OcorrenciaDtos.AberturaOcorrenciaRequest;
import br.com.fiap.luzvital.dto.OcorrenciaDtos.AparelhoCriticoResposta;
import br.com.fiap.luzvital.dto.OcorrenciaDtos.AtendimentoRequest;
import br.com.fiap.luzvital.dto.OcorrenciaDtos.AtendimentoResposta;
import br.com.fiap.luzvital.dto.OcorrenciaDtos.FilaItemResposta;
import br.com.fiap.luzvital.dto.OcorrenciaDtos.IndicadoresResposta;
import br.com.fiap.luzvital.dto.OcorrenciaDtos.NotificacaoResposta;
import br.com.fiap.luzvital.dto.OcorrenciaDtos.OcorrenciaResposta;
import br.com.fiap.luzvital.dto.PaginaResposta;
import br.com.fiap.luzvital.exception.ConflitoException;
import br.com.fiap.luzvital.exception.RecursoNaoEncontradoException;
import br.com.fiap.luzvital.exception.RegraNegocioException;
import br.com.fiap.luzvital.messaging.EnergiaCaiuEvento;
import br.com.fiap.luzvital.repository.OcorrenciaRepository;
import br.com.fiap.luzvital.repository.PacienteRepository;
import br.com.fiap.luzvital.repository.PontoEnergiaRepository;
import br.com.fiap.luzvital.security.UsuarioAutenticado;

/**
 * Ciclo de vida da ocorrência: "Estou sem luz" -> fila do gestor -> atendimentos -> encerramento.
 */
@Service
public class OcorrenciaService {

    private static final List<StatusOcorrencia> ATIVAS = List.of(StatusOcorrencia.EM_ABERTO, StatusOcorrencia.EM_ATENDIMENTO);

    private final OcorrenciaRepository ocorrencias;
    private final PacienteRepository pacientes;
    private final PontoEnergiaRepository pontos;
    private final PriorizacaoService priorizacao;
    private final EstadoOcorrencias estado;
    private final ApplicationEventPublisher eventos;

    public OcorrenciaService(OcorrenciaRepository ocorrencias, PacienteRepository pacientes,
                             PontoEnergiaRepository pontos, PriorizacaoService priorizacao,
                             EstadoOcorrencias estado, ApplicationEventPublisher eventos) {
        this.ocorrencias = ocorrencias;
        this.pacientes = pacientes;
        this.pontos = pontos;
        this.priorizacao = priorizacao;
        this.estado = estado;
        this.eventos = eventos;
    }

    /**
     * Abre a ocorrência. Idempotente: a mesma Idempotency-Key devolve a ocorrência já criada (criada=false).
     * Sem a chave, um segundo toque com ocorrência ativa devolve 409. A linha do paciente é travada para que
     * dois toques simultâneos não criem duas ocorrências.
     */
    @Transactional
    public ResultadoAbertura abrir(AberturaOcorrenciaRequest req, String chaveIdempotencia, UsuarioAutenticado usuario) {
        Paciente paciente = pacientes.findByIdParaAtualizacao(req.pacienteId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Paciente", req.pacienteId()));
        Acesso.escrita(paciente, usuario);
        LocalDateTime agora = LocalDateTime.now();

        if (chaveIdempotencia != null) {
            var existente = estado.ocorrenciaDaChave(chaveIdempotencia).flatMap(ocorrencias::findById);
            if (existente.isPresent()) {
                if (!existente.get().getPaciente().getId().equals(paciente.getId())) {
                    throw new ConflitoException("Esta Idempotency-Key já foi usada em outra ocorrência");
                }
                return new ResultadoAbertura(detalhe(existente.get(), agora), false);
            }
        }

        ocorrencias.findFirstByPacienteIdAndStatusIn(paciente.getId(), ATIVAS).ifPresent(ativa -> {
            throw new ConflitoException("Já existe uma ocorrência ativa (#" + ativa.getId() + ") para este paciente");
        });

        Equipamento critico = paciente.equipamentoCritico().orElseThrow(() -> new RegraNegocioException(
                "Cadastre ao menos um aparelho de suporte à vida antes de abrir uma ocorrência"));

        Ocorrencia ocorrencia = Ocorrencia.abrir(paciente, critico.getAutonomiaMinutos(), textoOuNulo(req.observacao()),
                agora);
        ocorrencias.save(ocorrencia);
        estado.registrarChave(chaveIdempotencia, ocorrencia.getId());
        // Publicado no RabbitMQ só após o commit (EventoPublisher)
        eventos.publishEvent(new EnergiaCaiuEvento(ocorrencia.getId(), paciente.getId(), paciente.getBairro(), agora));
        return new ResultadoAbertura(detalhe(ocorrencia, agora), true);
    }

    @Transactional(readOnly = true)
    public PaginaResposta<OcorrenciaResposta> listar(Long pacienteId, StatusOcorrencia status, Pageable pageable,
                                                     UsuarioAutenticado usuario) {
        LocalDateTime agora = LocalDateTime.now();
        var pagina = ocorrencias.buscar(Acesso.filtroCuidador(usuario), pacienteId, status, pageable);
        return PaginaResposta.de(pagina, o -> detalhe(o, agora));
    }

    @Transactional(readOnly = true)
    public OcorrenciaResposta buscar(Long id, UsuarioAutenticado usuario) {
        Ocorrencia o = carregar(id);
        Acesso.leitura(o.getPaciente(), usuario);
        return detalhe(o, LocalDateTime.now());
    }

    /** Fila do gestor: ocorrências ativas ordenadas pelos minutos de bateria que restam agora. */
    @Transactional(readOnly = true)
    public PaginaResposta<FilaItemResposta> fila(int pagina, int tamanho) {
        LocalDateTime agora = LocalDateTime.now();
        List<Ocorrencia> ordenadas = ocorrencias.findByStatusIn(ATIVAS).stream()
                .sorted(RegraPrioridade.ordemDaFila(o -> estado.minutosRestantes(o, agora)))
                .toList();
        List<FilaItemResposta> itens = new ArrayList<>();
        for (int i = 0; i < ordenadas.size(); i++) {
            itens.add(itemFila(i + 1, ordenadas.get(i), agora));
        }
        return PaginaResposta.deLista(itens, pagina, tamanho);
    }

    /** O cuidador (ou o gestor) informa quanto de bateria resta agora; a prioridade é recalculada na hora. */
    @Transactional
    public OcorrenciaResposta atualizarBateria(Long id, int minutosRestantes, UsuarioAutenticado usuario) {
        Ocorrencia o = carregar(id);
        if (!usuario.isGestor()) {
            Acesso.escrita(o.getPaciente(), usuario);
        }
        exigirAtiva(o);
        LocalDateTime agora = LocalDateTime.now();
        o.atualizarBateria(minutosRestantes);
        estado.registrarBateria(o.getId(), agora);
        priorizacao.recalcularScore(o, agora);
        return detalhe(o, agora);
    }

    @Transactional
    public AtendimentoResposta registrarAtendimento(Long id, AtendimentoRequest req) {
        Ocorrencia o = carregar(id);
        exigirAtiva(o);
        PontoEnergia ponto = null;
        if (req.pontoEnergiaId() != null) {
            ponto = pontos.findById(req.pontoEnergiaId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Ponto de energia", req.pontoEnergiaId()));
        }
        Atendimento atendimento = new Atendimento(o, ponto, req.tipoSocorro(), req.descricao().trim(),
                LocalDateTime.now());
        o.registrarAtendimento(atendimento);
        ocorrencias.flush();
        return AtendimentoResposta.de(atendimento);
    }

    @Transactional(readOnly = true)
    public List<AtendimentoResposta> listarAtendimentos(Long id, UsuarioAutenticado usuario) {
        Ocorrencia o = carregar(id);
        Acesso.leitura(o.getPaciente(), usuario);
        return o.getAtendimentos().stream().map(AtendimentoResposta::de).toList();
    }

    @Transactional
    public OcorrenciaResposta encerrar(Long id, String resultado, UsuarioAutenticado usuario) {
        Ocorrencia o = carregarParaFinalizar(id, usuario);
        LocalDateTime agora = LocalDateTime.now();
        o.encerrar(resultado.trim(), agora);
        return detalhe(o, agora);
    }

    @Transactional
    public OcorrenciaResposta cancelar(Long id, String motivo, UsuarioAutenticado usuario) {
        Ocorrencia o = carregarParaFinalizar(id, usuario);
        LocalDateTime agora = LocalDateTime.now();
        o.cancelar(motivo.trim(), agora);
        return detalhe(o, agora);
    }

    /** Indicadores de impacto da documentação (tempo de atendimento, resolvidas antes do fim da bateria...). */
    @Transactional(readOnly = true)
    public IndicadoresResposta indicadores() {
        List<Ocorrencia> todas = ocorrencias.findAll();
        // Registros da carga do banco podem estar ATENDIDO sem dh_fim: só entram nas médias os que têm fim
        List<Ocorrencia> atendidas = todas.stream()
                .filter(o -> o.getStatus() == StatusOcorrencia.ATENDIDO && o.getDataFim() != null).toList();
        Double tempoMedio = atendidas.isEmpty() ? null : arredondar(atendidas.stream()
                .mapToLong(o -> Duration.between(o.getDataInicio(), o.getDataFim()).toMinutes()).average().orElse(0));
        Double antesFimBateria = atendidas.isEmpty() ? null : arredondar(100.0 * atendidas.stream()
                .filter(o -> !o.getDataFim().isAfter(estado.previsaoEsgotamento(o))).count() / atendidas.size());
        Map<String, Long> pontosPorBairro = pontos.findByDisponivel(true).stream()
                .collect(Collectors.groupingBy(PontoEnergia::getBairro, TreeMap::new, Collectors.counting()));
        long totalAtendidas = todas.stream().filter(o -> o.getStatus() == StatusOcorrencia.ATENDIDO).count();
        return new IndicadoresResposta(todas.size(), todas.stream().filter(Ocorrencia::isAtiva).count(), totalAtendidas,
                todas.stream().filter(o -> o.getStatus() == StatusOcorrencia.CANCELADO).count(), tempoMedio,
                antesFimBateria, pacientes.count(), pontosPorBairro);
    }

    private Ocorrencia carregarParaFinalizar(Long id, UsuarioAutenticado usuario) {
        Ocorrencia o = carregar(id);
        if (!usuario.isGestor()) {
            Acesso.escrita(o.getPaciente(), usuario);
        }
        if (!o.isAtiva()) {
            throw new ConflitoException("A ocorrência #" + id + " já foi finalizada (" + o.getStatus() + ")");
        }
        return o;
    }

    private void exigirAtiva(Ocorrencia o) {
        if (!o.isAtiva()) {
            throw new ConflitoException("A ocorrência #" + o.getId() + " já foi finalizada (" + o.getStatus() + ")");
        }
    }

    private Ocorrencia carregar(Long id) {
        return ocorrencias.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Ocorrência", id));
    }

    private OcorrenciaResposta detalhe(Ocorrencia o, LocalDateTime agora) {
        Paciente p = o.getPaciente();
        return new OcorrenciaResposta(o.getId(), p.getId(), p.getNome(), p.getBairro(), o.getStatus(), o.getDataInicio(),
                o.getDataFim(), o.getBateriaMinutos(), estado.referenciaBateria(o),
                o.isAtiva() ? estado.minutosRestantes(o, agora) : 0, estado.previsaoEsgotamento(o), o.getPrioridadeScore(),
                o.isPriorizada(), o.getObservacao(), AparelhoCriticoResposta.de(p.equipamentoCritico().orElse(null)),
                o.getAtendimentos().stream().map(AtendimentoResposta::de).toList(),
                estado.avisos(o.getId()).stream().map(NotificacaoResposta::de).toList(),
                OcorrenciaDtos.AVISO_EMERGENCIA);
    }

    private FilaItemResposta itemFila(int posicao, Ocorrencia o, LocalDateTime agora) {
        Paciente p = o.getPaciente();
        return new FilaItemResposta(posicao, o.getId(), p.getId(), p.getNome(), p.getBairro(), p.getCidade(),
                p.getTelefone(), o.getStatus(), o.getDataInicio(), estado.minutosRestantes(o, agora),
                estado.previsaoEsgotamento(o), AparelhoCriticoResposta.de(p.equipamentoCritico().orElse(null)),
                p.possuiFonteReserva(), o.getPrioridadeScore(), o.isPriorizada(), o.getAtendimentos().size(),
                estado.avisos(o.getId()).size());
    }

    private static String textoOuNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static Double arredondar(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    public record ResultadoAbertura(OcorrenciaResposta ocorrencia, boolean criada) {
    }
}
