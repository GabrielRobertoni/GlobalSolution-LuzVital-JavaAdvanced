package br.com.fiap.luzvital.messaging;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

/**
 * Topologia do RabbitMQ (requisito 5, opção A).
 * <pre>
 * exchange topic  luzvital.eventos
 *   energia.caiu      -> fila luzvital.energia-caiu        -> consumidor calcula prioridade e avisa pontos de energia
 *   plano.solicitado  -> fila luzvital.plano-contingencia  -> consumidor gera o plano com Spring AI
 * exchange direct luzvital.eventos.dlx -> filas *.dlq (mensagens que falharam 3 vezes)
 * </pre>
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "luzvital.eventos";
    public static final String EXCHANGE_DLX = "luzvital.eventos.dlx";

    public static final String RK_ENERGIA_CAIU = "energia.caiu";
    public static final String RK_PLANO_SOLICITADO = "plano.solicitado";

    public static final String FILA_ENERGIA_CAIU = "luzvital.energia-caiu";
    public static final String FILA_PLANO = "luzvital.plano-contingencia";

    @Bean
    public Declarables topologiaLuzVital() {
        TopicExchange eventos = new TopicExchange(EXCHANGE, true, false);
        DirectExchange dlx = new DirectExchange(EXCHANGE_DLX, true, false);

        Queue energiaCaiu = fila(FILA_ENERGIA_CAIU);
        Queue energiaCaiuDlq = QueueBuilder.durable(FILA_ENERGIA_CAIU + ".dlq").build();
        Queue plano = fila(FILA_PLANO);
        Queue planoDlq = QueueBuilder.durable(FILA_PLANO + ".dlq").build();

        return new Declarables(eventos, dlx, energiaCaiu, energiaCaiuDlq, plano, planoDlq,
                BindingBuilder.bind(energiaCaiu).to(eventos).with(RK_ENERGIA_CAIU),
                BindingBuilder.bind(plano).to(eventos).with(RK_PLANO_SOLICITADO),
                BindingBuilder.bind(energiaCaiuDlq).to(dlx).with(FILA_ENERGIA_CAIU),
                BindingBuilder.bind(planoDlq).to(dlx).with(FILA_PLANO));
    }

    private static Queue fila(String nome) {
        return QueueBuilder.durable(nome)
                .deadLetterExchange(EXCHANGE_DLX)
                .deadLetterRoutingKey(nome)
                .build();
    }

    /** Mensagens em JSON; o tipo de destino é o parâmetro do @RabbitListener. */
    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jsonMapper);
        converter.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.INFERRED);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }
}
