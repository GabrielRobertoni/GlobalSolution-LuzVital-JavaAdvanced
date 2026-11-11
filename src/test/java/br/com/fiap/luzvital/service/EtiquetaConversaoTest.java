package br.com.fiap.luzvital.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import br.com.fiap.luzvital.domain.TipoAparelho;
import br.com.fiap.luzvital.dto.EquipamentoDtos.EtiquetaLidaResposta;
import br.com.fiap.luzvital.ia.EtiquetaExtraida;

class EtiquetaConversaoTest {

    @Test
    void etiquetaSoComVoltsEAmperesGeraPotenciaEstimada() {
        EtiquetaLidaResposta r = EquipamentoService.converter(new EtiquetaExtraida(true, "Philips Respironics",
                "EverFlo", "CONCENTRADOR_OXIGENIO", 230.0, 1.6, null, null));
        assertThat(r.potenciaWatts()).isEqualByComparingTo("368.00");
        assertThat(r.potenciaEstimada()).isTrue();
        assertThat(r.tipoAparelhoSugerido()).isEqualTo(TipoAparelho.CONCENTRADOR_OXIGENIO);
    }

    @Test
    void potenciaEscritaNaEtiquetaNaoEEstimada() {
        EtiquetaLidaResposta r = EquipamentoService.converter(new EtiquetaExtraida(true, "Philips", "Trilogy Evo",
                "tipo desconhecido", 240.0, 0.6, 120.0, null));
        assertThat(r.potenciaWatts()).isEqualByComparingTo("120.00");
        assertThat(r.potenciaEstimada()).isFalse();
        assertThat(r.tipoAparelhoSugerido()).isEqualTo(TipoAparelho.OUTRO);
    }

    @Test
    void fotoIlegivelPedeNovaFotoOuDigitacao() {
        EtiquetaLidaResposta r = EquipamentoService.converter(new EtiquetaExtraida(false, null, null, null, null, null,
                null, "Foto borrada"));
        assertThat(r.legivel()).isFalse();
        assertThat(r.aviso()).contains("tire outra foto");
    }
}
