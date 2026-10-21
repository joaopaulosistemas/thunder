package com.example.shop;

import static com.example.shop.TestTokens.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "features.bulk-discount=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderFlowTest {

    @Autowired
    MockMvc mvc;

    @Test
    void listsSeededProducts() throws Exception {
        mvc.perform(get("/api/products").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].sku").value("BK-001"));
    }

    @Test
    void createsOrderWithoutDiscountWhenFlagIsOff() throws Exception {
        mvc.perform(post("/api/orders").with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCents").value(89900))
                .andExpect(jsonPath("$.customer").value("alice"));
    }

    @Test
    void customerComesFromTokenNotFromBody() throws Exception {
        mvc.perform(post("/api/orders").with(user("carol"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":1,\"customer\":\"mallory\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customer").value("carol"));
    }

    @Test
    void rejectsUnknownProduct() throws Exception {
        mvc.perform(post("/api/orders").with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":999,\"quantity\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidQuantity() throws Exception {
        mvc.perform(post("/api/orders").with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":0}"))
                .andExpect(status().isBadRequest());
    }
}
