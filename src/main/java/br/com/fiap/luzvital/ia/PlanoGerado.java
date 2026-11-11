package br.com.fiap.luzvital.ia;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Saída estruturada do LLM para o plano de contingência (Spring AI converte o JSON da resposta
 * neste record). As descrições viram parte do JSON Schema enviado ao modelo.
 */
public record PlanoGerado(
        @JsonPropertyDescription("Resumo de 1 a 2 frases da situação da família, citando o aparelho mais crítico e quanto tempo de bateria ele tem")
        String resumo,
        @JsonPropertyDescription("Passos por faixa de tempo desde a queda de energia, em ordem cronológica")
        List<Etapa> etapas,
        @JsonPropertyDescription("Contatos úteis, usando apenas os números fornecidos no pedido")
        List<Contato> contatos,
        @JsonPropertyDescription("Sinais de alerta que indicam que é hora de ligar para o 192 imediatamente")
        List<String> sinaisAlerta,
        @JsonPropertyDescription("O que deixar pronto antes de um apagão (checklist curto)")
        List<String> preparacao) {

    public record Etapa(
            @JsonPropertyDescription("Faixa de tempo, ex.: 'Primeiros 30 minutos', 'De 30 minutos a 2 horas'")
            String faixaTempo,
            @JsonPropertyDescription("Ações objetivas para essa faixa de tempo")
            List<String> acoes) {
    }

    public record Contato(
            @JsonPropertyDescription("Nome do serviço ou da pessoa")
            String nome,
            @JsonPropertyDescription("Telefone exatamente como fornecido no pedido")
            String telefone,
            @JsonPropertyDescription("Quando acionar esse contato")
            String quandoAcionar) {
    }

    public boolean completo() {
        return resumo != null && !resumo.isBlank() && etapas != null && !etapas.isEmpty()
                && contatos != null && !contatos.isEmpty() && sinaisAlerta != null && !sinaisAlerta.isEmpty();
    }
}
