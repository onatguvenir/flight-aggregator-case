package com.technoly.infrastructure.client.soap.providerb;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import javax.xml.datatype.XMLGregorianCalendar;

/**
 * ProviderB SOAP isteği için request nesnesi.
 * ProviderA'dan farkı: departure ve arrival alanlarını kullanır.
 */
@XmlRootElement(name = "availabilitySearchRequest", namespace = "http://www.flightproviderb.com/ws")
@XmlAccessorType(XmlAccessType.FIELD)
public class AvailabilitySearchRequest {

    @XmlElement(namespace = "http://www.flightproviderb.com/ws")
    private String departure;

    @XmlElement(namespace = "http://www.flightproviderb.com/ws")
    private String arrival;

    @XmlElement(namespace = "http://www.flightproviderb.com/ws")
    private XMLGregorianCalendar departureDate;

    public String getDeparture() {
        return departure;
    }

    public void setDeparture(String departure) {
        this.departure = departure;
    }

    public String getArrival() {
        return arrival;
    }

    public void setArrival(String arrival) {
        this.arrival = arrival;
    }

    public XMLGregorianCalendar getDepartureDate() {
        return departureDate;
    }

    public void setDepartureDate(XMLGregorianCalendar departureDate) {
        this.departureDate = departureDate;
    }
}
