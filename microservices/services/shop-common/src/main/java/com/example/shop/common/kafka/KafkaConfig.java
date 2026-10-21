package com.example.shop.common.kafka;

import com.example.shop.contracts.Topics;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.stream.Stream;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    /**
     * Particoes por topico = maximo de consumidores ativos por grupo. Igual ao maxReplicas dos HPAs
     * (k8s): acima disso, replicas extras ficam ociosas no Kafka. Mude os dois juntos.
     */
    static final int PARTITIONS = 6;

    /** Cria os topicos (e as filas de falha ".DLT") se nao existirem. */
    @Bean
    KafkaAdmin.NewTopics shopTopics(@Value("${app.kafka.replicas:1}") int replicas) {
        List<String> names = List.of(
                Topics.PRODUCT_CHANGED, Topics.ORDER_CREATED,
                Topics.STOCK_RESERVED, Topics.STOCK_REJECTED, Topics.ORDER_CONFIRMED, Topics.ORDER_REJECTED,
                Topics.ORDER_CANCELLED, Topics.STOCK_RELEASE);
        // O DeadLetterPublishingRecoverer publica na MESMA particao do registro original; por isso a
        // DLT precisa ter pelo menos as mesmas particoes (senao o envio falha e a mensagem se perde).
        NewTopic[] all = names.stream()
                .flatMap(n -> Stream.of(n, n + ".DLT"))
                .map(n -> topic(n, replicas))
                .toArray(NewTopic[]::new);
        return new KafkaAdmin.NewTopics(all);
    }

    /**
     * Mensagem que falha 4 vezes (1 s entre elas) vai para o topico "<nome>.DLT" em vez de travar a
     * particao. Alerte sobre mensagens na DLT e reprocesse depois de corrigir a causa.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template, MeterRegistry registry) {
        DeadLetterPublishingRecoverer toDlt = new DeadLetterPublishingRecoverer(template);
        // Conta cada mensagem enviada a DLT (metrica shop_dlt_messages_total{topic}): alerta DeadLetterGrowing.
        return new DefaultErrorHandler((record, error) -> {
            registry.counter("shop.dlt.messages", "topic", record.topic()).increment();
            toDlt.accept(record, error);
        }, new FixedBackOff(1000L, 3L));
    }

    private static NewTopic topic(String name, int replicas) {
        return TopicBuilder.name(name).partitions(PARTITIONS).replicas(replicas).build();
    }
}
