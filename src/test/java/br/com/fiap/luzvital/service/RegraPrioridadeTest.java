package br.com.fiap.luzvital.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.fiap.luzvital.domain.Equipamento;
import br.com.fiap.luzvital.domain.FonteReserva;
import br.com.fiap.luzvital.domain.Ocorrencia;
import br.com.fiap.luzvital.domain.Paciente;
import br.com.fiap.luzvital.domain.TipoAparelho;

class RegraPrioridadeTest {

    private final LocalDateTime t0 = LocalDateTime.of(2026, 11, 10, 20, 0);
    private final EstadoOcorrencias estado = new EstadoOcorrencias();

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
        Ocorrencia o = abrir(1L, TipoAparelho.VENTILADOR_MECANICO, 120, t0);
        assertThat(estado.minutosRestantes(o, t0)).isEqualTo(120);
        assertThat(estado.minutosRestantes(o, t0.plusMinutes(45))).isEqualTo(75);
        assertThat(estado.minutosRestantes(o, t0.plusHours(5))).isZero();
        assertThat(estado.previsaoEsgotamento(o)).isEqualTo(t0.plusMinutes(120));
    }

    @Test
    void atualizarBateriaReiniciaAContagemAPartirDoNovoValor() {
        Ocorrencia o = abrir(2L, TipoAparelho.VENTILADOR_MECANICO, 900, t0);
        o.atualizarBateria(30);
        estado.registrarBateria(o.getId(), t0.plusHours(2));
        assertThat(estado.minutosRestantes(o, t0.plusHours(2).plusMinutes(10))).isEqualTo(20);
        assertThat(estado.previsaoEsgotamento(o)).isEqualTo(t0.plusHours(2).plusMinutes(30));
    }

    @Test
    void semAtualizacaoDeBateriaAContagemUsaDhInicio() {
        Ocorrencia o = Ocorrencia.abrir(paciente(TipoAparelho.VENTILADOR_MECANICO, 60), 60, null, t0);
        assertThat(estado.minutosRestantes(o, t0.plusMinutes(15))).isEqualTo(45);
    }

    @Test
    void filaOrdenaPorMinutosRestantesENoEmpateVentiladorAntesDeConcentrador() {
        Ocorrencia concentrador = abrir(10L, TipoAparelho.CONCENTRADOR_OXIGENIO, 60, t0);
        Ocorrencia ventilador = abrir(11L, TipoAparelho.VENTILADOR_MECANICO, 60, t0);
        Ocorrencia muitaBateria = abrir(12L, TipoAparelho.VENTILADOR_MECANICO, 900, t0);
        // Chegou depois, mas tem menos bateria: passa na frente
        Ocorrencia semBateria = abrir(13L, TipoAparelho.CONCENTRADOR_OXIGENIO, 0, t0.plusMinutes(5));

        LocalDateTime agora = t0.plusMinutes(5);
        List<Ocorrencia> fila = new ArrayList<>(List.of(muitaBateria, concentrador, ventilador, semBateria));
        fila.sort(RegraPrioridade.ordemDaFila(o -> estado.minutosRestantes(o, agora)));

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

    private Ocorrencia abrir(Long id, TipoAparelho tipo, int bateria, LocalDateTime inicio) {
        Ocorrencia o = Ocorrencia.abrir(paciente(tipo, bateria), bateria, null, inicio);
        ReflectionTestUtils.setField(o, "id", id);
        return o;
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
