package br.com.fiap.luzvital;

import java.util.Locale;
import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class LuzVitalApplication {

    public static void main(String[] args) {
        // A contagem regressiva da bateria usa o horário de São Paulo em todo o sistema
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        usarCertificadosDoWindows();
        SpringApplication.run(LuzVitalApplication.class, args);
    }

    /**
     * No Windows, as chamadas HTTPS (ex.: provedor de IA) passam a confiar nos certificados do próprio Windows.
     * Isso evita o erro "unable to find valid certification path" em redes ou antivírus que inspecionam HTTPS.
     * Não altera nada se a JVM já recebeu -Djavax.net.ssl.trustStoreType, fora do Windows (Linux, Mac, Docker)
     * ou com a variável de ambiente LUZVITAL_CERTIFICADOS_WINDOWS=false.
     */
    static void usarCertificadosDoWindows() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        boolean jaConfigurado = System.getProperty("javax.net.ssl.trustStoreType") != null;
        boolean desligado = "false".equalsIgnoreCase(System.getenv("LUZVITAL_CERTIFICADOS_WINDOWS"));
        if (windows && !jaConfigurado && !desligado) {
            System.setProperty("javax.net.ssl.trustStoreType", "Windows-ROOT");
        }
    }
}
