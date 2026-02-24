package com.technoly.infrastructure.masking;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * PII (Personally Identifiable Information) Masking Annotation
 *
 * Fields annotated with this are automatically masked during JSON
 * serialization:
 * "John Doe" → "Jo***oe"
 *
 * Located in the Infrastructure module because jackson-databind
 * (required for MaskingSerializer) causes a Lombok AP conflict in the domain
 * module.
 *
 * Scope: Only personal data (for KVKK / GDPR compliance):
 * - passengerName : Passenger full name
 * - email : E-mail address
 * - phone : Phone number
 * - passportNo : Passport number
 *
 * @JacksonAnnotationsInside: Composite annotation pattern.
 *                            Automatically applies Jackson's @JsonSerialize
 *                            to fields that use this annotation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@JacksonAnnotationsInside
@JsonSerialize(using = MaskingSerializer.class)
public @interface Masked {
}
