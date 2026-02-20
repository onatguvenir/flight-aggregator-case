package com.technoly.infrastructure.client.soap.providerb;

import javax.xml.datatype.XMLGregorianCalendar;
import java.math.BigDecimal;

/**
 * ProviderB SOAP servisinden dönen uçuş tipi.
 * ProviderA'dan fark: flightNumber, departure, arrival alan adları.
 */
public class FlightType {
    private String flightNumber;
    private String departure;
    private String arrival;
    private XMLGregorianCalendar departuredatetime;
    private XMLGregorianCalendar arrivaldatetime;
    private BigDecimal price;

    public String getFlightNumber() {
        return flightNumber;
    }

    public void setFlightNumber(String flightNumber) {
        this.flightNumber = flightNumber;
    }

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
