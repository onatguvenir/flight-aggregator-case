package com.technoly.infrastructure.client.soap.providera;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * ProviderA SOAP yanıt nesnesi.
 * Spring-WS bu nesneye deserialize eder.
 */
@XmlRootElement(name = "availabilitySearchResponse", namespace = "http://www.flightprovidera.com/ws")
@XmlAccessorType(XmlAccessType.FIELD)
public class AvailabilitySearchResponse {

    @XmlElement(namespace = "http://www.flightprovidera.com/ws")
    private boolean hasError;

    @XmlElement(namespace = "http://www.flightprovidera.com/ws")
    private String errorMessage;

    @XmlElement(name = "flightOptions", namespace = "http://www.flightprovidera.com/ws")
    private List<FlightType> flightOptions = new ArrayList<>();

    public boolean isHasError() {
        return hasError;
    }

    public void setHasError(boolean hasError) {
        this.hasError = hasError;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<FlightType> getFlightOptions() {
        return flightOptions;
    }

    public void setFlightOptions(List<FlightType> flightOptions) {
        this.flightOptions = flightOptions;
    }
}
