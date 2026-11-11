package br.com.fiap.luzvital.ia;

import java.util.ArrayList;
import java.util.List;

import br.com.fiap.luzvital.domain.FonteReserva;
import br.com.fiap.luzvital.domain.TipoAparelho;

/**
 * Plano de contingência por regras fixas, usado quando a IA não está configurada ou falha.
 * Garante que a família sempre tenha um plano salvo no celular, mesmo sem o LLM.
 */
public final class PlanoPadrao {

    private PlanoPadrao() {
    }

    public static PlanoGerado montar(ContextoPlano ctx) {
        int autonomia = ctx.menorAutonomiaCritica();
        List<ContextoPlano.Aparelho> criticos = ctx.suporteVida().isEmpty() ? ctx.aparelhos() : ctx.suporteVida();
        boolean semBateria = criticos.stream().anyMatch(a -> a.autonomiaMinutos() == 0);
        boolean temReserva = criticos.stream().anyMatch(a -> a.fonteReserva() != FonteReserva.NENHUMA);
        boolean temConcentrador = criticos.stream().anyMatch(a -> a.tipo() == TipoAparelho.CONCENTRADOR_OXIGENIO);

        String resumo = semBateria
                ? "Há aparelho de suporte à vida sem bateria: ele para assim que a luz cai. A fonte reserva e o pedido de ajuda são imediatos."
                : "O aparelho mais crítico tem cerca de " + formatarTempo(autonomia)
                  + " de bateria. Avise pelo app logo no início para entrar na fila do gestor.";

        List<PlanoGerado.Etapa> etapas = new ArrayList<>();
        List<String> primeiros = new ArrayList<>();
        primeiros.add("Toque em \"Estou sem luz\" no app para abrir a ocorrência e iniciar a contagem da bateria.");
        if (temReserva) {
            primeiros.add("Ligue a fonte reserva cadastrada (bateria externa, cilindro ou gerador) e confira se o aparelho está funcionando.");
        } else {
            primeiros.add("Sem fonte reserva cadastrada: ligue para a distribuidora e informe que há paciente eletrodependente na casa.");
        }
        if (temConcentrador) {
            primeiros.add("Se usar cilindro de oxigênio, abra-o conforme a orientação recebida da equipe de saúde.");
        }
        primeiros.add("Mantenha o celular carregando no que tiver disponível e economize bateria.");
        etapas.add(new PlanoGerado.Etapa("Primeiros 30 minutos", primeiros));

        etapas.add(new PlanoGerado.Etapa("De 30 minutos a 2 horas", List.of(
                "Atualize no app quanto tempo de bateria resta no aparelho.",
                "Fale com o ponto de energia mais próximo indicado pelo app ou pelo gestor.",
                "Separe documentos, receitas e carregadores caso seja preciso sair de casa.")));

        etapas.add(new PlanoGerado.Etapa("Quando faltar 1 hora de bateria", List.of(
                "Ligue de novo para a distribuidora e informe o tempo que resta.",
                "Combine com o gestor a remoção do paciente ou a recarga da bateria em um ponto de energia.",
                "Se não houver resposta e a bateria estiver acabando, ligue 192.")));

        List<PlanoGerado.Contato> contatos = new ArrayList<>();
        contatos.add(new PlanoGerado.Contato("SAMU", "192", "Qualquer sinal de alerta no paciente"));
        contatos.add(new PlanoGerado.Contato("Corpo de Bombeiros", "193", "Risco de incêndio, choque ou remoção urgente"));
        contatos.add(new PlanoGerado.Contato("Defesa Civil", "199", "Apagão prolongado na região"));
        contatos.add(new PlanoGerado.Contato("Distribuidora de energia", "Número impresso na conta de luz",
                "Logo no início da queda e quando faltar 1 hora de bateria"));
        for (ContextoPlano.Ponto p : ctx.pontosEnergia()) {
            contatos.add(new PlanoGerado.Contato(p.nome(), p.telefone(),
                    p.possuiGerador() ? "Recarga de bateria com gerador" : "Apoio próximo de casa"));
        }

        List<String> sinais = List.of(
                "Falta de ar, respiração muito rápida ou esforço para respirar",
                "Lábios ou pontas dos dedos arroxeados",
                "Sonolência fora do normal, confusão ou desmaio",
                "Alarme do aparelho que não para mesmo com a fonte reserva ligada");

        List<String> preparacao = new ArrayList<>(List.of(
                "Manter o cadastro de aparelhos atualizado no app.",
                "Deixar a bateria do aparelho e a do celular sempre carregadas.",
                "Ter lanterna, carregador portátil e a lista de contatos impressa.",
                "Manter o cadastro de cliente eletrodependente na distribuidora renovado todo ano."));
        if (!temReserva) {
            preparacao.add("Conversar com a equipe de saúde sobre uma fonte reserva (bateria externa ou cilindro).");
        }
        return new PlanoGerado(resumo, etapas, contatos, sinais, preparacao);
    }

    static String formatarTempo(int minutos) {
        if (minutos < 60) {
            return minutos + " minutos";
        }
        int h = minutos / 60;
        int m = minutos % 60;
        return m == 0 ? h + " h" : h + " h " + m + " min";
    }
}
