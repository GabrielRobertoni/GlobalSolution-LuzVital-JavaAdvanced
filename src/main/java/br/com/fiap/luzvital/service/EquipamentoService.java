package br.com.fiap.luzvital.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeType;
import org.springframework.web.multipart.MultipartFile;

import br.com.fiap.luzvital.domain.Equipamento;
import br.com.fiap.luzvital.domain.Paciente;
import br.com.fiap.luzvital.domain.TipoAparelho;
import br.com.fiap.luzvital.dto.EquipamentoDtos.EquipamentoRequest;
import br.com.fiap.luzvital.dto.EquipamentoDtos.EquipamentoResposta;
import br.com.fiap.luzvital.dto.EquipamentoDtos.EtiquetaLidaResposta;
import br.com.fiap.luzvital.dto.PaginaResposta;
import br.com.fiap.luzvital.exception.RecursoNaoEncontradoException;
import br.com.fiap.luzvital.exception.RegraNegocioException;
import br.com.fiap.luzvital.ia.EtiquetaExtraida;
import br.com.fiap.luzvital.ia.IaService;
import br.com.fiap.luzvital.repository.EquipamentoRepository;
import br.com.fiap.luzvital.repository.PacienteRepository;
import br.com.fiap.luzvital.security.UsuarioAutenticado;

@Service
public class EquipamentoService {

    private static final Set<String> TIPOS_IMAGEM = Set.of("image/jpeg", "image/png", "image/webp");
    private static final String AVISO_ETIQUETA = "Leitura automática por IA: confira cada campo antes de salvar. "
            + "A autonomia da bateria não aparece na etiqueta e deve ser informada pelo cuidador.";

    private final EquipamentoRepository equipamentos;
    private final PacienteRepository pacientes;
    private final PlanoContingenciaService planoService;
    private final IaService ia;

    public EquipamentoService(EquipamentoRepository equipamentos, PacienteRepository pacientes,
                              PlanoContingenciaService planoService, IaService ia) {
        this.equipamentos = equipamentos;
        this.pacientes = pacientes;
        this.planoService = planoService;
        this.ia = ia;
    }

    @Transactional(readOnly = true)
    public PaginaResposta<EquipamentoResposta> listar(Long pacienteId, TipoAparelho tipo, Boolean suporteVida,
                                                      Pageable pageable, UsuarioAutenticado usuario) {
        var pagina = equipamentos.buscar(Acesso.filtroCuidador(usuario), pacienteId, tipo, suporteVida, pageable);
        return PaginaResposta.de(pagina, EquipamentoResposta::de);
    }

    @Transactional(readOnly = true)
    public EquipamentoResposta buscar(Long id, UsuarioAutenticado usuario) {
        Equipamento e = carregar(id);
        Acesso.leitura(e.getPaciente(), usuario);
        return EquipamentoResposta.de(e);
    }

    /** Salvar um aparelho dispara a geração do plano de contingência pela IA (evento plano.solicitado). */
    @Transactional
    public EquipamentoResposta criar(EquipamentoRequest req, UsuarioAutenticado usuario) {
        Paciente paciente = pacientes.findById(req.pacienteId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Paciente", req.pacienteId()));
        Acesso.escrita(paciente, usuario);
        Equipamento e = new Equipamento(paciente);
        aplicar(e, req);
        paciente.getEquipamentos().add(e);
        equipamentos.save(e);
        planoService.solicitar(paciente, "aparelho cadastrado");
        return EquipamentoResposta.de(e);
    }

    @Transactional
    public EquipamentoResposta atualizar(Long id, EquipamentoRequest req, UsuarioAutenticado usuario) {
        Equipamento e = carregar(id);
        Acesso.escrita(e.getPaciente(), usuario);
        if (!e.getPaciente().getId().equals(req.pacienteId())) {
            throw new RegraNegocioException("Não é possível mover o aparelho para outro paciente; exclua e cadastre de novo");
        }
        aplicar(e, req);
        planoService.solicitar(e.getPaciente(), "aparelho alterado");
        return EquipamentoResposta.de(e);
    }

    @Transactional
    public void excluir(Long id, UsuarioAutenticado usuario) {
        Equipamento e = carregar(id);
        Paciente paciente = e.getPaciente();
        Acesso.escrita(paciente, usuario);
        paciente.getEquipamentos().remove(e); // orphanRemoval apaga o registro
        planoService.solicitar(paciente, "aparelho excluído");
    }

    /** Cadastro por foto: o modelo multimodal lê a etiqueta; nada é salvo até o cuidador confirmar. */
    public EtiquetaLidaResposta lerEtiqueta(MultipartFile foto) {
        if (foto == null || foto.isEmpty()) {
            throw new RegraNegocioException("Envie a foto da etiqueta no campo 'foto'");
        }
        String tipo = foto.getContentType();
        if (tipo == null || !TIPOS_IMAGEM.contains(tipo.toLowerCase())) {
            throw new RegraNegocioException("Formato de imagem não suportado: use JPEG, PNG ou WEBP");
        }
        byte[] bytes;
        try {
            bytes = foto.getBytes();
        } catch (java.io.IOException ex) {
            throw new RegraNegocioException("Não foi possível ler o arquivo enviado");
        }
        EtiquetaExtraida lida = ia.lerEtiqueta(bytes, MimeType.valueOf(tipo));
        return converter(lida);
    }

    static EtiquetaLidaResposta converter(EtiquetaExtraida lida) {
        if (lida == null || !lida.legivel()) {
            return new EtiquetaLidaResposta(false, null, null, null, null, null, null, false,
                    lida == null ? "Não foi possível ler a etiqueta" : lida.observacoes(),
                    "Etiqueta ilegível: tire outra foto com boa luz e sem reflexo, ou preencha manualmente.");
        }
        BigDecimal tensao = decimal(lida.tensaoVolts());
        BigDecimal corrente = decimal(lida.correnteAmperes());
        BigDecimal potencia = decimal(lida.potenciaWatts());
        boolean estimada = false;
        if (potencia == null && tensao != null && corrente != null) {
            // Etiqueta só com volts e ampères: potência aparente (V x A), marcada como estimada
            potencia = tensao.multiply(corrente).setScale(2, RoundingMode.HALF_UP);
            estimada = true;
        }
        return new EtiquetaLidaResposta(true, lida.fabricante(), lida.modelo(), tipo(lida.tipoAparelho()), tensao,
                corrente, potencia, estimada, lida.observacoes(), AVISO_ETIQUETA);
    }

    private static TipoAparelho tipo(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return TipoAparelho.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return TipoAparelho.OUTRO;
        }
    }

    private static BigDecimal decimal(Double d) {
        return d == null || d <= 0 ? null : BigDecimal.valueOf(d).setScale(2, RoundingMode.HALF_UP);
    }

    private Equipamento carregar(Long id) {
        return equipamentos.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Equipamento", id));
    }

    private void aplicar(Equipamento e, EquipamentoRequest req) {
        e.atualizar(req.tipoAparelho(), req.fabricante().trim(), req.modelo().trim(), req.potenciaWatts(),
                req.autonomiaMinutos(), req.suporteVida(), req.fonteReserva());
    }
}
