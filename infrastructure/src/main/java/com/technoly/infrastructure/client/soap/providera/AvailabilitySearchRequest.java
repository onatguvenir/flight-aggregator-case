package com.technoly.infrastructure.client.soap.providera;

import javax.xml.datatype.XMLGregorianCalendar;

/**
 * ProviderA SOAP isteği için gerekli request nesnesi.
 * JAXB annotation ile XML element olarak serialize edilir:
 * @XmlRootElement(name="availabilitySearchRequest", namespace="http://www.flightprovidera.com/ws")
 *
 * Spring-WS'nin marshalSendAndReceive metodu bu nesneyi SOAP mesajına çevirir.
 */
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "availabilitySearchRequest", namespace = "http://www.flightprovidera.com/ws")
@XmlAccessorType(XmlAccessType.FIELD)
public class AvailabilitySearchRequest {

    @XmlElement(namespace = "http://www.flightprovidera.com/ws")
    private String origin;

    @XmlElement(namespace = "http://www.flightprovidera.com/ws")
    private String destination;

    @XmlElement(namespace = "http://www.flightprovidera.com/ws")
    private XMLGregorianCalendar departureDate;

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

    public XMLGregorianCalendar getDepartureDate() {
        return departureDate;
    }

    public void setDepartureDate(XMLGregorianCalendar departureDate) {
        this.departureDate = departureDate;
    }
}
