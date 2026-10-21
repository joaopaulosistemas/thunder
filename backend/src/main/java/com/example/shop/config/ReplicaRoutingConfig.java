package com.example.shop.config;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Ativa a separacao leitura/escrita quando APP_DATASOURCE_REPLICA_URL esta definida:
 * transacoes readOnly=true vao para a replica; o resto vai para o primario.
 * A replica e eventualmente consistente: leituras logo apos uma escrita podem estar defasadas.
 */
@Configuration
@ConditionalOnProperty(name = "app.datasource.replica-url")
class ReplicaRoutingConfig {

    enum Route { PRIMARY, REPLICA }

    static class ReadWriteRoutingDataSource extends AbstractRoutingDataSource {
        @Override
        protected Object determineCurrentLookupKey() {
            return TransactionSynchronizationManager.isCurrentTransactionReadOnly() ? Route.REPLICA : Route.PRIMARY;
        }
    }

    @Bean
    @Primary
    DataSource dataSource(DataSourceProperties properties, @Value("${app.datasource.replica-url}") String replicaUrl) {
        HikariDataSource primary = properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        primary.setPoolName("primary");

        HikariDataSource replica = properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        replica.setJdbcUrl(replicaUrl);
        replica.setReadOnly(true);
        replica.setPoolName("replica");

        ReadWriteRoutingDataSource routing = new ReadWriteRoutingDataSource();
        routing.setTargetDataSources(Map.of(Route.PRIMARY, primary, Route.REPLICA, replica));
        routing.setDefaultTargetDataSource(primary);
        routing.afterPropertiesSet();
        return new LazyConnectionDataSourceProxy(routing);
    }
}
