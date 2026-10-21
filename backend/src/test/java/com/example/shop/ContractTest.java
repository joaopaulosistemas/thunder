package com.example.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.File;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API contract-first: o arquivo api/openapi.yaml manda. Se alguem criar, remover ou renomear um
 * endpoint sem atualizar o contrato (ou o contrario), este teste quebra o build.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContractTest {

    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");

    @Autowired
    MockMvc mvc;

    @Test
    void implementedEndpointsMatchTheContract() throws Exception {
        JsonNode contract = new YAMLMapper().readTree(new File("../api/openapi.yaml"));
        String generatedJson = mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        JsonNode generated = new ObjectMapper().readTree(generatedJson);

        assertThat(operations(generated))
                .as("endpoints implementados (springdoc) x contrato (api/openapi.yaml)")
                .isEqualTo(operations(contract));
    }

    private static Set<String> operations(JsonNode spec) {
        Set<String> result = new TreeSet<>();
        Iterator<Map.Entry<String, JsonNode>> paths = spec.path("paths").fields();
        while (paths.hasNext()) {
            Map.Entry<String, JsonNode> path = paths.next();
            Iterator<String> methods = path.getValue().fieldNames();
            while (methods.hasNext()) {
                String method = methods.next();
                if (HTTP_METHODS.contains(method)) {
                    result.add(method.toUpperCase(Locale.ROOT) + " " + path.getKey());
                }
            }
        }
        return result;
    }
}
