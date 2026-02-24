package com.technoly.infrastructure.masking;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

/**
 * PII Data Masking Serializer
 *
 * Integrates with Jackson's @JsonSerialize mechanism.
 * 
 * Automatically applies to fields annotated with @Masked.
 *
 * Located in the infrastructure module because jackson-databind StdSerializer
 * creates an incompatibility with the domain module's annotation processor
 * (Lombok).
 *
 * Masking Rules:
 * - null → null (unchanged)
 * - 1-4 characters → "***" (fully hidden)
 * - 5+ characters → first 2 + "***" + last 2 visible
 *
 * Examples:
 * "Ali" → "***"
 * "John Doe" → "Jo***oe"
 * "+905551234567" → "+9***67"
 * "john@ex.com" → "jo***om"
 *
 * PII Fields (under KVKK / GDPR context):
 * - passengerName, email, phone, passportNo
 */
public class MaskingSerializer extends StdSerializer<String> {

    private static final int VISIBLE_CHARS = 2;
    private static final String MASK = "***";

    public MaskingSerializer() {
        super(String.class);
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider provider)
            throws IOException {

        if (value == null) {
            gen.writeNull();
            return;
        }

        gen.writeString(mask(value));
    }

    /**
     * Masking logic.
     * Pure function: same input always gives same output → testability.
     *
     * @param value String to be masked
     * @return Masked string
     */
    public static String mask(String value) {
        if (value == null)
            return null;
        int length = value.length();
        // Very short values are fully hidden
        if (length <= VISIBLE_CHARS * 2) {
            return MASK;
        }
        // First VISIBLE_CHARS chars + MASK + last VISIBLE_CHARS chars
        return value.substring(0, VISIBLE_CHARS)
                + MASK
                + value.substring(length - VISIBLE_CHARS);
    }
}
