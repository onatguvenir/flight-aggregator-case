package com.technoly.domain.port;

import com.technoly.domain.model.FlightDto;
import com.technoly.domain.model.FlightSearchRequest;

import java.util.List;

/**
 * Uçuş aramasını tüm sağlayıcılar (provider'lar) üzerinden orkestre eden Port.
 *
 * Bu interface, Hexagonal Architecture / Ports & Adapters yaklaşımında
 * application katmanının "dış dünya" detaylarına (SOAP, HTTP, vendor DTO'ları)
 * bağımlı olmadan uçuş araması yapabilmesini sağlar.
 *
 * Beklenen davranış:
 * - Provider'lardan gelen sonuçlar domain'de kullandığımız ortak modele (FlightDto)
 *   normalize edilmelidir.
 * - Tek bir provider hata verdiğinde tüm arama akışı çökmez; mümkünse
 *   "kısmi başarı" ile diğer provider sonuçları döndürülür.
 * - Hata/sonuç yok durumunda null yerine boş liste dönülmesi tercih edilir
 *   (null-safety).
 */
public interface FlightSearchPort {
    List<FlightDto> searchAllFlights(FlightSearchRequest request);
}
