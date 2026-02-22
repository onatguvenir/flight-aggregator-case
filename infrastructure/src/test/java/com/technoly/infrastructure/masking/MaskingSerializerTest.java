package com.technoly.infrastructure.masking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PII Maskeleme Serializer Testleri
 *
 * MaskingSerializer.mask() pure function olduğundan
 * Jackson altyapısı olmadan doğrudan test edilebilir.
 */
@DisplayName("PII Data Masking Tests")
class MaskingSerializerTest {

    @Test
    @DisplayName("null değer → null döner")
    void nullValueReturnsNull() {
        assertThat(MaskingSerializer.mask(null)).isNull();
    }

    @Test
    @DisplayName("Kısa değer (<=4 karakter) → '***' ile tam maskeleme")
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
    @DisplayName("Normal PII değerleri kısmen maskelenir")
    void normalPiiIsPartiallyMasked(String input, String expected) {
        assertThat(MaskingSerializer.mask(input.trim())).isEqualTo(expected.trim());
    }

    @Test
    @DisplayName("Kesinlikle 5 karakterlik değer → ilk 2 + *** + son 2")
    void fiveCharValueIsPartiallyMasked() {
        assertThat(MaskingSerializer.mask("ABCDE")).isEqualTo("AB***DE");
    }
}
