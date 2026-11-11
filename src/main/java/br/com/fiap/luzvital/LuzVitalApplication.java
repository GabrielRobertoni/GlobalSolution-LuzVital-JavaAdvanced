package br.com.fiap.luzvital;

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
        SpringApplication.run(LuzVitalApplication.class, args);
    }
}
