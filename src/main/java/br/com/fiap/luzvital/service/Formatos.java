package br.com.fiap.luzvital.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Normalização de dados e cálculo de distância. */
public final class Formatos {

    private static final double RAIO_TERRA_KM = 6371.0;

    private Formatos() {
    }

    /** CPF no formato da coluna Oracle VARCHAR2(14): 000.000.000-00. */
    public static String cpf(String valor) {
        String d = valor.replaceAll("\\D", "");
        return d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9, 11);
    }

    /** CEP no formato 00000-000. */
    public static String cep(String valor) {
        String d = valor.replaceAll("\\D", "");
        return d.substring(0, 5) + "-" + d.substring(5, 8);
    }

    /** Distância em km entre duas coordenadas (fórmula de haversine). */
    public static BigDecimal distanciaKm(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        double phi1 = Math.toRadians(lat1.doubleValue());
        double phi2 = Math.toRadians(lat2.doubleValue());
        double dPhi = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double dLambda = Math.toRadians(lon2.doubleValue() - lon1.doubleValue());
        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        double km = RAIO_TERRA_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return BigDecimal.valueOf(km).setScale(2, RoundingMode.HALF_UP);
    }

    /** Compara bairros ignorando maiúsculas, acentos e espaços extras. */
    public static boolean mesmoBairro(String a, String b) {
        return a != null && b != null && normalizar(a).equals(normalizar(b));
    }

    private static String normalizar(String s) {
        return java.text.Normalizer.normalize(s.trim().toLowerCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("\\s+", " ");
    }
}
