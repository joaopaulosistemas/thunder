package com.example.shop;

import static com.example.shop.TestTokens.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Os outros testes usam H2 (rapido), que aceita SQL que o PostgreSQL recusa. Este roda as
 * migracoes Flyway e a validacao do Hibernate (ddl-auto=validate) num PostgreSQL de verdade.
 * Sem Docker na maquina, o teste e pulado (no CI roda).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PostgresMigrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void migrationsApplyOnRealPostgresAndOrdersPersist() throws Exception {
        assertThat(jdbc.queryForObject("select version()", String.class)).contains("PostgreSQL");
        assertThat(jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = true", Integer.class)).isEqualTo(3);

        mvc.perform(post("/api/orders").with(user("pg-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":2}"))
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForObject(
                "select count(*) from orders where customer = 'pg-user'", Integer.class)).isEqualTo(1);
    }
}
