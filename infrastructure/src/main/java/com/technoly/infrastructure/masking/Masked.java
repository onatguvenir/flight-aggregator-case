package com.technoly.infrastructure.masking;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * PII (Personally Identifiable Information) Maskeleme Annotasyonu
 *
 * Bu annotasyon ile işaretlenen alanlar, JSON serializasyonu sırasında
 * otomatik olarak maskelenir: "John Doe" → "Jo***oe"
 *
 * Infrastructure modülünde yer alır çünkü jackson-databind
 * (MaskingSerializer için gerekli) domain modülünde Lombok AP çakışmasına yol
 * açar.
 *
 * Kapsam: Sadece kişisel veriler (KVKK / GDPR uyumluluğu için):
 * - passengerName : Yolcu adı soyadı
 * - email : E-posta adresi
 * - phone : Telefon numarası
 * - passportNo : Pasaport numarası
 *
 * @JacksonAnnotationsInside: Kompozit annotation örüntüsü.
 *                            Bu annotasyonu kullanan alanlara
 *                            Jackson @JsonSerialize'i otomatik uygular.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@JacksonAnnotationsInside
@JsonSerialize(using = MaskingSerializer.class)
public @interface Masked {
}
