package com.technoly.infrastructure.client.soap.providerb;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * ProviderB SOAP yanıt nesnesi.
 */
@XmlRootElement(name = "availabilitySearchResponse", namespace = "http://www.flightproviderb.com/ws")
@XmlAccessorType(XmlAccessType.FIELD)
public class AvailabilitySearchResponse {

    @XmlElement(namespace = "http://www.flightproviderb.com/ws")
    private boolean hasError;

    @XmlElement(namespace = "http://www.flightproviderb.com/ws")
    private String errorMessage;

    @XmlElement(name = "flightOptions", namespace = "http://www.flightproviderb.com/ws")
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
