package com.example.shop;

import static com.example.shop.TestTokens.user;
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

@SpringBootTest(properties = "features.bulk-discount=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BulkDiscountFlagTest {

    @Autowired
    MockMvc mvc;

    @Test
    void appliesTenPercentDiscountFromTenUnits() throws Exception {
        mvc.perform(post("/api/orders").with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCents").value(80910));
    }

    @Test
    void doesNotDiscountBelowThreshold() throws Exception {
        mvc.perform(post("/api/orders").with(user("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":9}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCents").value(80910));
    }
}
