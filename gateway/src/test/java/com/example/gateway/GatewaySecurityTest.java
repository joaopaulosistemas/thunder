package com.example.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Regras de borda. O upstream (localhost:1) nao existe de proposito: o que se verifica e QUEM e
 * barrado antes de chegar nele (401/403). Quem passa na seguranca falha ao conectar (nem 401 nem 403).
 */
@SpringBootTest
@ActiveProfiles("test")
class GatewaySecurityTest {

    @Autowired ApplicationContext context;
    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToApplicationContext(context).apply(springSecurity()).configureClient().build();
    }

    @Test
    void semTokenRecebe401() {
        client.get().uri("/api/products").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void tokenInvalidoRecebe401() {
        client.get().uri("/api/products").header("Authorization", "Bearer lixo").exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void userNaoAcessaRotaAdmin() {
        client.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_user")))
                .get().uri("/api/admin/summary").exchange().expectStatus().isForbidden();
    }

    @Test
    void userPassaDaSegurancaNaRotaDeApi() {
        client.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_user")))
                .get().uri("/api/products").exchange()
                .expectStatus().value(s -> assertThat(s).isNotIn(401, 403));
    }

    @Test
    void adminPassaDaSegurancaNaRotaAdmin() {
        client.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_admin")))
                .get().uri("/api/admin/summary").exchange()
                .expectStatus().value(s -> assertThat(s).isNotIn(401, 403));
    }

    @Test
    void rotaForaDeApiENegada() {
        client.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_admin")))
                .get().uri("/qualquer-coisa").exchange().expectStatus().isForbidden();
    }
}
