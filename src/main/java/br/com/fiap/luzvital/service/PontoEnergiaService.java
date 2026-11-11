package br.com.fiap.luzvital.service;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.fiap.luzvital.domain.PontoEnergia;
import br.com.fiap.luzvital.dto.PaginaResposta;
import br.com.fiap.luzvital.dto.PontoEnergiaDtos.PontoEnergiaRequest;
import br.com.fiap.luzvital.dto.PontoEnergiaDtos.PontoEnergiaResposta;
import br.com.fiap.luzvital.exception.ConflitoException;
import br.com.fiap.luzvital.exception.RecursoNaoEncontradoException;
import br.com.fiap.luzvital.repository.AtendimentoRepository;
import br.com.fiap.luzvital.repository.NotificacaoPontoRepository;
import br.com.fiap.luzvital.repository.PontoEnergiaRepository;

@Service
public class PontoEnergiaService {

    private final PontoEnergiaRepository pontos;
    private final NotificacaoPontoRepository notificacoes;
    private final AtendimentoRepository atendimentos;

    public PontoEnergiaService(PontoEnergiaRepository pontos, NotificacaoPontoRepository notificacoes,
                               AtendimentoRepository atendimentos) {
        this.pontos = pontos;
        this.notificacoes = notificacoes;
        this.atendimentos = atendimentos;
    }

    @Transactional(readOnly = true)
    public PaginaResposta<PontoEnergiaResposta> listar(String bairro, Boolean disponivel, Boolean possuiGerador,
                                                       Pageable pageable) {
        String filtroBairro = bairro == null || bairro.isBlank() ? null : bairro.trim();
        return PaginaResposta.de(pontos.buscar(filtroBairro, disponivel, possuiGerador, pageable), PontoEnergiaResposta::de);
    }

    @Transactional(readOnly = true)
    public PontoEnergiaResposta buscar(Long id) {
        return PontoEnergiaResposta.de(carregar(id));
    }

    @Transactional
    public PontoEnergiaResposta criar(PontoEnergiaRequest req) {
        PontoEnergia ponto = new PontoEnergia();
        aplicar(ponto, req);
        return PontoEnergiaResposta.de(pontos.save(ponto));
    }

    @Transactional
    public PontoEnergiaResposta atualizar(Long id, PontoEnergiaRequest req) {
        PontoEnergia ponto = carregar(id);
        aplicar(ponto, req);
        return PontoEnergiaResposta.de(ponto);
    }

    @Transactional
    public void excluir(Long id) {
        PontoEnergia ponto = carregar(id);
        if (notificacoes.existsByPontoEnergiaId(id) || atendimentos.existsByPontoEnergiaId(id)) {
            throw new ConflitoException("Ponto com histórico de ocorrências não pode ser excluído; "
                    + "altere 'disponivel' para false para tirá-lo da rede");
        }
        pontos.delete(ponto);
    }

    PontoEnergia carregar(Long id) {
        return pontos.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Ponto de energia", id));
    }

    private void aplicar(PontoEnergia p, PontoEnergiaRequest req) {
        p.atualizar(req.nomeLocal().trim(), req.tipoEstabelecimento(), req.responsavel().trim(), req.telefone().trim(),
                req.bairro().trim(), req.possuiGerador(), req.disponivel(), req.latitude(), req.longitude());
    }
}
