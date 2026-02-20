package com.technoly.domain.port;

import com.technoly.domain.model.FlightDto;
import com.technoly.domain.model.FlightSearchRequest;

import java.util.List;

/**
 * Flight Provider Port Interface (Hexagonal Architecture / Port & Adapter)
 *
 * Bu interface, application katmanının infrastructure katmanına bağımlı
 * olmamasını sağlar. Strategy Pattern uygulanır:
 * - FlightProviderAClient -> FlightProviderPort implements
 * - FlightProviderBClient -> FlightProviderPort implements
 *
 * Application servisi her iki implementasyonla da List<FlightProviderPort>
 * aracılığıyla çalışabilir. Yeni bir provider eklendiğinde sadece
 * bu interface'i implement eden yeni bir sınıf yazılır (OCP - Open/Closed
 * Principle).
 *
 * Dependency Inversion Principle (DIP):
 * - Yüksek seviye (application) → soyutlamaya (port) bağımlı
 * - Düşük seviye (infrastructure) → soyutlamayı implemente eder
 */
public interface FlightProviderPort {

    /**
     * Sağlayıcıdan uçuş müsaitlik araması yapar.
     *
     * @param request Arama parametreleri (origin, destination, departureDate)
     * @return Sağlayıcıdan dönen normalize edilmiş FlightDto listesi.
     *         Hata veya boş sonuç durumunda boş liste döner (null değil).
     */
    List<FlightDto> searchFlights(FlightSearchRequest request);

    /**
     * Bu provider'ın adını döner (loglama ve metrikler için).
     * Örnek: "PROVIDER_A", "PROVIDER_B"
     */
    String getProviderName();
}
