package br.com.fiap.luzvital.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** Envelope de paginação estável para o app (não expõe a estrutura interna do Spring Data). */
public record PaginaResposta<T>(
        List<T> conteudo,
        int pagina,
        int tamanho,
        long totalElementos,
        int totalPaginas,
        boolean ultima) {

    public static <E, T> PaginaResposta<T> de(Page<E> page, Function<E, T> mapper) {
        return new PaginaResposta<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    public static <T> PaginaResposta<T> deLista(List<T> todos, int pagina, int tamanho) {
        int total = todos.size();
        int inicio = Math.min(pagina * tamanho, total);
        int fim = Math.min(inicio + tamanho, total);
        int totalPaginas = tamanho == 0 ? 0 : (int) Math.ceil((double) total / tamanho);
        return new PaginaResposta<>(todos.subList(inicio, fim), pagina, tamanho, total, totalPaginas, fim >= total);
    }
}
