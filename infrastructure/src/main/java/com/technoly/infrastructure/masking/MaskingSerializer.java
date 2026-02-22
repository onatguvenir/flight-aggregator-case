package com.technoly.infrastructure.masking;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

/**
 * PII Veri Maskeleme Serializer
 *
 * Jackson @JsonSerialize mekanizması ile entegre çalışır.
 * 
 * @Masked annotasyonu ile işaretlenen alanlara otomatik uygulanır.
 *
 *         Infrastructure modülünde bulunur çünkü jackson-databind StdSerializer
 *         domain modülünün annotation processor'u (Lombok) ile uyumsuzluk
 *         yaratır.
 *
 *         Maskeleme Kuralları:
 *         - null → null (değişmez)
 *         - 1-4 karakter → "***" (tamamı gizlenir)
 *         - 5+ karakter → ilk 2 + "***" + son 2 görünür
 *
 *         Örnekler:
 *         "Ali" → "***"
 *         "John Doe" → "Jo***oe"
 *         "+905551234567" → "+9***67"
 *         "john@ex.com" → "jo***om"
 *
 *         PII Alanları (KVKK / GDPR kapsamında):
 *         - passengerName, email, phone, passportNo
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
     * Maskeleme mantığı.
     * Pure function: aynı girdi daima aynı çıktı → test edilebilirlik.
     *
     * @param value Maskelenecek string
     * @return Maskelenmiş string
     */
    public static String mask(String value) {
        if (value == null)
            return null;
        int length = value.length();
        // Çok kısa değerlerin tamamı gizlenir
        if (length <= VISIBLE_CHARS * 2) {
            return MASK;
        }
        // Başından VISIBLE_CHARS karakter + MASK + sondan VISIBLE_CHARS karakter
        return value.substring(0, VISIBLE_CHARS)
                + MASK
                + value.substring(length - VISIBLE_CHARS);
    }
}
