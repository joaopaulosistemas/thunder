package com.example.shop.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductSpecsTest {

    @Test
    void curingasDigitadosPeloUsuarioViramTextoComum() {
        assertThat(ProductSpecs.escape("100%_off\\")).isEqualTo("100\\%\\_off\\\\");
    }
}
