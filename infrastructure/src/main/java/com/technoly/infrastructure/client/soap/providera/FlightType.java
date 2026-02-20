package com.technoly.infrastructure.client.soap.providera;

import javax.xml.datatype.XMLGregorianCalendar;
import java.math.BigDecimal;

/**
 * ProviderA SOAP servisinden dönen uçuş tipi (JAXB generated sınıf wrapper'ı).
 *
 * Gerçek bir projede bu sınıf jaxb2-maven-plugin ile XSD'den otomatik generate
 * edilirdi.
 * Burada manuel olarak yazılıyor çünkü aggregator bir client olarak çalışır ve
 * provider'ın XSD'sini import eder.
 *
 * Bu sınıf, FlightProviderAClient içinde kullanılır ve ProviderA'nın alan
 * adlarını
 * (flightNo, origin, destination) korur. Normalizasyon mapping sırasında
 * yapılır.
 */
public class FlightType {
    private String flightNo;
    private String origin;
    private String destination;
    private XMLGregorianCalendar departuredatetime;
    private XMLGregorianCalendar arrivaldatetime;
    private BigDecimal price;

    public String getFlightNo() {
        return flightNo;
    }

    public void setFlightNo(String flightNo) {
        this.flightNo = flightNo;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public XMLGregorianCalendar getDeparturedatetime() {
        return departuredatetime;
    }

    public void setDeparturedatetime(XMLGregorianCalendar departuredatetime) {
        this.departuredatetime = departuredatetime;
    }

    public XMLGregorianCalendar getArrivaldatetime() {
        return arrivaldatetime;
    }

    public void setArrivaldatetime(XMLGregorianCalendar arrivaldatetime) {
        this.arrivaldatetime = arrivaldatetime;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}
