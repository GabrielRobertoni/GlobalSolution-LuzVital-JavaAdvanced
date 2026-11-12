package br.com.fiap.luzvital.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.fiap.luzvital.domain.Equipamento;
import br.com.fiap.luzvital.domain.FonteReserva;
import br.com.fiap.luzvital.domain.Ocorrencia;
import br.com.fiap.luzvital.domain.Paciente;
import br.com.fiap.luzvital.domain.TipoAparelho;

class RegraPrioridadeTest {

    private final LocalDateTime t0 = LocalDateTime.of(2026, 11, 10, 20, 0);

    @Test
    void scoreIgualAFuncaoOracleFnTGsCalculaPrioridade() {
        // Faixas de FN_T_GS_CALCULA_PRIORIDADE, +200 quando o paciente usa aparelho de suporte à vida
        assertThat(RegraPrioridade.calcularScore(0, true)).isEqualTo(1200);
        assertThat(RegraPrioridade.calcularScore(0, false)).isEqualTo(1000);
        assertThat(RegraPrioridade.calcularScore(1, true)).isEqualTo(1059);
        assertThat(RegraPrioridade.calcularScore(45, true)).isEqualTo(1015);
        assertThat(RegraPrioridade.calcularScore(60, true)).isEqualTo(1000);
        assertThat(RegraPrioridade.calcularScore(61, false)).isEqualTo(619);
        assertThat(RegraPrioridade.calcularScore(180, false)).isEqualTo(500);
        assertThat(RegraPrioridade.calcularScore(900, true)).isEqualTo(300);
        assertThat(RegraPrioridade.calcularScore(900, false)).isEqualTo(100);
    }

    @Test
    void minutosRestantesDescontaOTempoDesdeAQuedaENaoFicaNegativo() {
        Ocorrencia o = Ocorrencia.abrir(paciente(TipoAparelho.VENTILADOR_MECANICO, 120), 120, null, null, t0);
        assertThat(o.minutosRestantes(t0)).isEqualTo(120);
        assertThat(o.minutosRestantes(t0.plusMinutes(45))).isEqualTo(75);
        assertThat(o.minutosRestantes(t0.plusHours(5))).isZero();
        assertThat(o.previsaoEsgotamento()).isEqualTo(t0.plusMinutes(120));
    }

    @Test
    void atualizarBateriaReiniciaAContagemAPartirDoNovoValor() {
        Ocorrencia o = Ocorrencia.abrir(paciente(TipoAparelho.VENTILADOR_MECANICO, 900), 900, null, null, t0);
        o.atualizarBateria(30, t0.plusHours(2));
        assertThat(o.minutosRestantes(t0.plusHours(2).plusMinutes(10))).isEqualTo(20);
    }

    @Test
    void filaOrdenaPorMinutosRestantesENoEmpateVentiladorAntesDeConcentrador() {
        Ocorrencia concentrador = Ocorrencia.abrir(paciente(TipoAparelho.CONCENTRADOR_OXIGENIO, 60), 60, null, null, t0);
        Ocorrencia ventilador = Ocorrencia.abrir(paciente(TipoAparelho.VENTILADOR_MECANICO, 60), 60, null, null, t0);
        Ocorrencia muitaBateria = Ocorrencia.abrir(paciente(TipoAparelho.VENTILADOR_MECANICO, 900), 900, null, null, t0);
        // Chegou depois, mas tem menos bateria: passa na frente
        Ocorrencia semBateria = Ocorrencia.abrir(paciente(TipoAparelho.CONCENTRADOR_OXIGENIO, 0), 0, null, null, t0.plusMinutes(5));

        List<Ocorrencia> fila = new ArrayList<>(List.of(muitaBateria, concentrador, ventilador, semBateria));
        fila.sort(RegraPrioridade.ordemDaFila(t0.plusMinutes(5)));

        assertThat(fila).containsExactly(semBateria, ventilador, concentrador, muitaBateria);
    }

    @Test
    void aparelhoCriticoEOdeSuporteAVidaComMenorAutonomia() {
        Paciente p = new Paciente(null);
        adicionar(p, TipoAparelho.BOMBA_INFUSAO, 10, false, FonteReserva.NENHUMA);
        adicionar(p, TipoAparelho.VENTILADOR_MECANICO, 900, true, FonteReserva.BATERIA_EXTERNA);
        adicionar(p, TipoAparelho.CONCENTRADOR_OXIGENIO, 0, true, FonteReserva.NENHUMA);

        assertThat(p.equipamentoCritico()).get().extracting(Equipamento::getTipoAparelho)
                .isEqualTo(TipoAparelho.CONCENTRADOR_OXIGENIO);
        assertThat(p.possuiFonteReserva()).isTrue();
    }

    @Test
    void bairroIgnoraAcentoEMaiusculas() {
        assertThat(Formatos.mesmoBairro("Consolação", "  consolacao ")).isTrue();
        assertThat(Formatos.mesmoBairro("Bela Vista", "Pinheiros")).isFalse();
    }

    private Paciente paciente(TipoAparelho tipo, int autonomia) {
        Paciente p = new Paciente(null);
        adicionar(p, tipo, autonomia, true, FonteReserva.NENHUMA);
        return p;
    }

    private void adicionar(Paciente p, TipoAparelho tipo, int autonomia, boolean suporteVida, FonteReserva reserva) {
        Equipamento e = new Equipamento(p);
        e.atualizar(tipo, "Fab", "Mod", new BigDecimal("100"), autonomia, suporteVida, reserva);
        p.getEquipamentos().add(e);
    }
}
