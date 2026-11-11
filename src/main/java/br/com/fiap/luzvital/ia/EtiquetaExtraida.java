package br.com.fiap.luzvital.ia;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** Saída estruturada do modelo multimodal ao ler a etiqueta do aparelho. */
public record EtiquetaExtraida(
        @JsonPropertyDescription("false se a foto estiver borrada, com reflexo forte ou não mostrar uma etiqueta de aparelho")
        boolean legivel,
        @JsonPropertyDescription("Fabricante exatamente como escrito na etiqueta, ou null")
        String fabricante,
        @JsonPropertyDescription("Modelo exatamente como escrito na etiqueta, ou null")
        String modelo,
        @JsonPropertyDescription("Um destes valores, ou null: VENTILADOR_MECANICO, CONCENTRADOR_OXIGENIO, ASPIRADOR_SECRECAO, DIALISE_DOMICILIAR, BOMBA_INFUSAO, CADEIRA_RODAS_ELETRICA, OUTRO")
        String tipoAparelho,
        @JsonPropertyDescription("Tensão nominal em volts; se for faixa (100-240 V), use o maior valor; null se não houver")
        Double tensaoVolts,
        @JsonPropertyDescription("Corrente em ampères; se for faixa, use o maior valor; null se não houver")
        Double correnteAmperes,
        @JsonPropertyDescription("Potência em watts somente se estiver escrita na etiqueta; não calcule; null se não houver")
        Double potenciaWatts,
        @JsonPropertyDescription("Problemas na leitura (reflexo, parte cortada, campo duvidoso) em uma frase, ou null")
        String observacoes) {
}
