package com.flightproviderb.endpoint;

import com.flightprovider.wsdl.Flight;
import com.flightprovider.wsdl.SearchRequest;
import com.flightprovider.wsdl.SearchResult;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Random;

@Endpoint
public class FlightEndpoint {

    // Uçuş tarihlerini String olarak gönderip alırken kullandığımız ve uygulamamızın standardı olan tarih format kalıbı.
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy'T'HH:mm");

    private static final String NAMESPACE_URI = "http://flightprovider.com/wsdl";

    @PayloadRoot(namespace = NAMESPACE_URI, localPart = "SearchRequest")
    @ResponsePayload
    public SearchResult availabilitySearch(@RequestPayload SearchRequest request) {
        SearchResult result = new SearchResult();

        if (request == null ||
                request.getOrigin() == null || request.getOrigin().trim().isEmpty() ||
                request.getDestination() == null || request.getDestination().trim().isEmpty() ||
                request.getDepartureDate() == null || request.getDepartureDate().trim().isEmpty()) {

            result.setHasError(true);
            result.setErrorMessage("Invalid search parameters. Origin, destination, and departure date are required.");
            return result;
        }

        LocalDateTime departureDate;
        try {
            // Aggregator (Tüketici) tarafından iletilen yeni standardımızdaki uçuş tarihi String metnini,
            // DATE_FORMATTER kullanarak LocalDateTime nesnesine çeviriyoruz ki üzerinde tarihsel kontroller yapabilelim (örn. gelecek tarih mi?).
            departureDate = LocalDateTime.parse(request.getDepartureDate(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            // Eğer Aggregator geçerli formatta bir tarih yollamaz ise uçuştan hata dönüyoruz.
            // Bu yaklaşım Guardian Clause tarzı "Erken Dönüş" (Early Return) presibini yansıtır.
            result.setHasError(true);
            result.setErrorMessage("Invalid departure date format. Expected (e.g. 01-06-2026T10:00).");
            return result;
        }

        if (departureDate.isBefore(LocalDateTime.now())) {
            result.setHasError(true);
            result.setErrorMessage("Departure date cannot be in the past.");
            return result;
        }

        try {
            Random random = new Random();

            for (int i = 1; i <= 3; i++) {
                LocalDateTime departureTime = departureDate
                        .withHour(6 + (i * 3))
                        .withMinute(0)
                        .withSecond(0);

                Duration flightDuration = Duration.ofMinutes(
                        120 + (long) (random.nextDouble() * 180));

                LocalDateTime arrivalTime = departureTime.plus(flightDuration);

                BigDecimal basePrice = BigDecimal.valueOf(100.0);
                BigDecimal multiplier = BigDecimal.valueOf(i * 50);
                BigDecimal randomPrice = BigDecimal.valueOf(random.nextInt(100));
                BigDecimal totalPrice = basePrice.add(multiplier).add(randomPrice);

                Flight f1 = new Flight();
                f1.setFlightNumber("TK" + (1000 + i));
                f1.setOrigin("IST");
                f1.setDestination("COV");
                // Tarih objemizi (LocalDateTime) SOAP üzerinden dış dünyaya (Ağa) gönderirken 
                // metne dönüştürerek serialize ediyoruz. (dd-MM-yyyy'T'HH:mm formatında)
                f1.setDepartureTime(departureTime.format(DATE_FORMATTER));
                f1.setArrivalTime(arrivalTime.format(DATE_FORMATTER));
                f1.setPrice(totalPrice);
                result.getFlights().add(f1);

                Flight f2 = new Flight();
                f2.setFlightNumber("PC" + (1000 + i));
                f2.setOrigin("IST");
                f2.setDestination("COV");
                f2.setDepartureTime(departureTime.format(DATE_FORMATTER));
                f2.setArrivalTime(arrivalTime.format(DATE_FORMATTER));
                f2.setPrice(totalPrice);
                result.getFlights().add(f2);

                Flight f3 = new Flight();
                f3.setFlightNumber("XQ" + (1000 + i));
                f3.setOrigin("IST");
                f3.setDestination("COV");
                f3.setDepartureTime(departureTime.format(DATE_FORMATTER));
                f3.setArrivalTime(arrivalTime.format(DATE_FORMATTER));
                f3.setPrice(totalPrice);
                result.getFlights().add(f3);
            }
            result.setHasError(false);

        } catch (Exception e) {
            result.setHasError(true);
            result.setErrorMessage("An error occurred while searching for flights: " + e.getMessage());
        }

        return result;
    }
}
