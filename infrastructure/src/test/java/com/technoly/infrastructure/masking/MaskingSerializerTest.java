package com.technoly.infrastructure.masking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PII Masking Serializer Tests
 *
 * Since MaskingSerializer.mask() is a pure function
 * it can be tested directly without the Jackson infra.
 */
@DisplayName("PII Data Masking Tests")
class MaskingSerializerTest {

    @Test
    @DisplayName("null value → returns null")
    void nullValueReturnsNull() {
        assertThat(MaskingSerializer.mask(null)).isNull();
    }

    @Test
    @DisplayName("Short value (<=4 chars) → full masking with '***'")
    void shortValueIsFullyMasked() {
        assertThat(MaskingSerializer.mask("Ali")).isEqualTo("***");
        assertThat(MaskingSerializer.mask("ab")).isEqualTo("***");
        assertThat(MaskingSerializer.mask("A")).isEqualTo("***");
    }

    @ParameterizedTest(name = "[{index}] {0} → {1}")
    @CsvSource({
            "John Doe,       Jo***oe",
            "john@ex.com,    jo***om",
            "+905551234567,  +9***67",
            "TR123456789,    TR***89",
    })
    @DisplayName("Normal PII values are partially masked")
    void normalPiiIsPartiallyMasked(String input, String expected) {
        assertThat(MaskingSerializer.mask(input.trim())).isEqualTo(expected.trim());
    }

    @Test
    @DisplayName("Exactly 5 character value → first 2 + *** + last 2")
    void fiveCharValueIsPartiallyMasked() {
        assertThat(MaskingSerializer.mask("ABCDE")).isEqualTo("AB***DE");
    }
}
