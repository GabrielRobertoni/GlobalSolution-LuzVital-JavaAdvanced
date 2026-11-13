package br.com.fiap.luzvital.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Os códigos gravados e lidos batem com os valores do script Oracle da disciplina de banco. */
class CodigoBancoTest {

    @Test
    void tipoSocorroGravaExatamenteComoAConstraintOracleExige() {
        TipoSocorro.Conversor c = new TipoSocorro.Conversor();
        assertThat(c.convertToDatabaseColumn(TipoSocorro.REMOCAO_UTI)).isEqualTo("REMOÇAO_UTI");
        assertThat(c.convertToDatabaseColumn(TipoSocorro.RELIGACAO_PRIORITARIA)).isEqualTo("RELIGAÇÃO_PRIORITARIA");
        assertThat(c.convertToDatabaseColumn(TipoSocorro.GERADOR_MOVEL)).isEqualTo("GERADOR_MOVEL");
        assertThat(c.convertToEntityAttribute("REMOÇAO_UTI")).isEqualTo(TipoSocorro.REMOCAO_UTI);
        assertThat(c.convertToEntityAttribute("RELIGACAO_PRIORITARIA")).isEqualTo(TipoSocorro.RELIGACAO_PRIORITARIA);
    }

    @Test
    void aparelhoComAcentoDaCargaOracleELidoNormalmente() {
        TipoAparelho.Conversor c = new TipoAparelho.Conversor();
        assertThat(c.convertToEntityAttribute("ASPIRADOR_SECREÇÃO")).isEqualTo(TipoAparelho.ASPIRADOR_SECRECAO);
        assertThat(c.convertToEntityAttribute("ventilador_mecanico")).isEqualTo(TipoAparelho.VENTILADOR_MECANICO);
        assertThat(c.convertToEntityAttribute("TIPO_NOVO")).isEqualTo(TipoAparelho.OUTRO);
        // Grava como na carga do script Oracle, para o filtro por tipo encontrar os registros antigos e os novos
        assertThat(c.convertToDatabaseColumn(TipoAparelho.ASPIRADOR_SECRECAO)).isEqualTo("ASPIRADOR_SECREÇÃO");
        assertThat(c.convertToDatabaseColumn(TipoAparelho.VENTILADOR_MECANICO)).isEqualTo("VENTILADOR_MECANICO");
    }

    @Test
    void estabelecimentoPublicoDaCargaOracle() {
        assertThat(new TipoEstabelecimento.Conversor().convertToEntityAttribute("PUBLICO"))
                .isEqualTo(TipoEstabelecimento.PUBLICO);
        assertThat(new SimNaoConverter().convertToEntityAttribute("S")).isTrue();
        assertThat(new SimNaoConverter().convertToDatabaseColumn(false)).isEqualTo("N");
    }
}
