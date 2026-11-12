package br.com.fiap.luzvital.ia;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import br.com.fiap.luzvital.domain.Equipamento;
import br.com.fiap.luzvital.domain.FonteReserva;
import br.com.fiap.luzvital.domain.Paciente;
import br.com.fiap.luzvital.domain.PontoEnergia;
import br.com.fiap.luzvital.domain.TipoAparelho;

/**
 * Dados mínimos enviados ao LLM para montar o plano. Por LGPD não vão nome, CPF nem endereço do
 * paciente: só bairro, aparelhos, o telefone do cuidador e os pontos de energia do bairro.
 */
public record ContextoPlano(
        String bairro,
        String cidade,
        String telefone,
        List<Aparelho> aparelhos,
        List<Ponto> pontosEnergia) {

    public record Aparelho(TipoAparelho tipo, String fabricante, String modelo, BigDecimal potenciaWatts,
                           int autonomiaMinutos, boolean suporteVida, FonteReserva fonteReserva,
                           BigDecimal horasComEstacao1kWh) {
    }

    public record Ponto(String nome, String telefone, String bairro, boolean possuiGerador) {
    }

    public static ContextoPlano de(Paciente paciente, List<PontoEnergia> pontos) {
        List<Aparelho> aparelhos = paciente.getEquipamentos().stream().map(ContextoPlano::aparelho).toList();
        List<Ponto> pontosInfo = pontos.stream()
                .map(p -> new Ponto(p.getNomeLocal(), p.getTelefone(), p.getBairro(), p.isPossuiGerador()))
                .toList();
        return new ContextoPlano(paciente.getBairro(), paciente.getCidade(), paciente.getTelefone(), aparelhos,
                pontosInfo);
    }

    private static Aparelho aparelho(Equipamento e) {
        BigDecimal horas = BigDecimal.valueOf(1000).divide(e.getPotenciaWatts(), 1, RoundingMode.HALF_UP);
        return new Aparelho(e.getTipoAparelho(), e.getFabricante(), e.getModelo(), e.getPotenciaWatts(),
                e.getAutonomiaMinutos(), e.isSuporteVida(), e.getFonteReserva(), horas);
    }

    public List<Aparelho> suporteVida() {
        return aparelhos.stream().filter(Aparelho::suporteVida).toList();
    }

    /** Menor autonomia entre os aparelhos de suporte à vida (ou entre todos, se nenhum for de suporte à vida). */
    public int menorAutonomiaCritica() {
        List<Aparelho> base = suporteVida().isEmpty() ? aparelhos : suporteVida();
        return base.stream().mapToInt(Aparelho::autonomiaMinutos).min().orElse(0);
    }

    /** Texto com os dados do paciente para o prompt (sem chaves { } para não conflitar com o template). */
    public String descricaoParaPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("Local: bairro ").append(bairro).append(", ").append(cidade).append('\n');
        sb.append("Telefone de contato da família: ").append(telefone).append('\n');
        sb.append("Menor autonomia entre os aparelhos de suporte à vida: ").append(menorAutonomiaCritica())
                .append(" minutos\n");
        sb.append("Aparelhos cadastrados:\n");
        for (Aparelho a : aparelhos) {
            sb.append("- ").append(a.tipo()).append(" ").append(a.fabricante()).append(" ").append(a.modelo())
                    .append(" | potência ").append(a.potenciaWatts()).append(" W")
                    .append(" | autonomia da bateria ").append(a.autonomiaMinutos()).append(" min")
                    .append(a.autonomiaMinutos() == 0 ? " (sem bateria: para assim que a luz cai)" : "")
                    .append(" | suporte à vida: ").append(a.suporteVida() ? "sim" : "não")
                    .append(" | fonte reserva: ").append(a.fonteReserva())
                    .append(" | uma estação de energia de 1 kWh mantém este aparelho por cerca de ")
                    .append(a.horasComEstacao1kWh()).append(" h\n");
        }
        sb.append("Pontos de energia voluntários no bairro:\n");
        if (pontosEnergia.isEmpty()) {
            sb.append("- nenhum ponto cadastrado no bairro\n");
        }
        for (Ponto p : pontosEnergia) {
            sb.append("- ").append(p.nome()).append(" | telefone ").append(p.telefone())
                    .append(" | gerador: ").append(p.possuiGerador() ? "sim" : "não").append('\n');
        }
        sb.append("Contatos oficiais permitidos: SAMU 192; Bombeiros 193; Defesa Civil 199; ")
                .append("distribuidora de energia (número impresso na conta de luz).\n");
        // Texto livre digitado pelo usuário não pode ser interpretado como variável do template do Spring AI
        return sb.toString().replace('{', '(').replace('}', ')');
    }
}
