package br.com.fiap.luzvital.service;

import java.text.Normalizer;
import java.util.Locale;

/** Normalização dos dados no formato das colunas do banco. */
public final class Formatos {

    private Formatos() {
    }

    /** CPF no formato da coluna nr_cpf VARCHAR2(14): 000.000.000-00. */
    public static String cpf(String valor) {
        String d = valor.replaceAll("\\D", "");
        return d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9, 11);
    }

    /** CEP no formato 00000-000. */
    public static String cep(String valor) {
        String d = valor.replaceAll("\\D", "");
        return d.substring(0, 5) + "-" + d.substring(5, 8);
    }

    /** Compara bairros ignorando maiúsculas, acentos e espaços extras. */
    public static boolean mesmoBairro(String a, String b) {
        return a != null && b != null && normalizar(a).equals(normalizar(b));
    }

    private static String normalizar(String s) {
        return Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("\\s+", " ");
    }
}
