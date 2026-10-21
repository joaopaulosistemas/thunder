package com.example.shop;

import static com.example.shop.TestTokens.admin;
import static com.example.shop.TestTokens.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Contrato de seguranca: quem entra, com qual role, e o que cada um enxerga. */
@SpringBootTest
@AutoConfigureMockMvc
// Em testes o Spring Boot desliga a exportacao de metricas: sem isto /actuator/prometheus responde 404
@AutoConfigureObservability
@ActiveProfiles("test")
class SecurityTest {

    @Autowired
    MockMvc mvc;

    @Test
    void apiWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":1,\"quantity\":1}")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithoutKnownRoleIs403() throws Exception {
        mvc.perform(get("/api/products").with(jwt())).andExpect(status().isForbidden());
    }

    @Test
    void healthAndPrometheusAreOpenForProbesAndScraping() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
    }

    @Test
    void adminEndpointNeedsAdminRole() throws Exception {
        mvc.perform(get("/api/admin/summary").with(user("alice"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/summary").with(admin("bob")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders").isNumber())
                .andExpect(jsonPath("$.revenueCents").isNumber());
    }

    @Test
    void userSeesOnlyOwnOrdersButAdminSeesAll() throws Exception {
        mvc.perform(post("/api/orders").with(user("dave-sec"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":1}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/orders").with(user("erin-sec")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.customer=='dave-sec')]").isEmpty());

        mvc.perform(get("/api/orders").with(user("dave-sec")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.customer=='dave-sec')]").isNotEmpty());

        mvc.perform(get("/api/orders").with(admin("bob")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.customer=='dave-sec')]").isNotEmpty());
    }

    @Test
    void meEndpointExposesWhatTheBackendSawInTheToken() throws Exception {
        // Um unico .jwt(...): chamar .jwt() de novo substitui o anterior e perderia subject e preferred_username
        var bob = jwt().jwt(j -> j.subject("bob").claim("preferred_username", "bob").claim("email", "bob@example.com"))
                .authorities(new SimpleGrantedAuthority("ROLE_user"), new SimpleGrantedAuthority("ROLE_admin"));
        mvc.perform(get("/api/me").with(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("bob"))
                .andExpect(jsonPath("$.email").value("bob@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("admin"))
                .andExpect(jsonPath("$.roles[1]").value("user"));
    }
}
