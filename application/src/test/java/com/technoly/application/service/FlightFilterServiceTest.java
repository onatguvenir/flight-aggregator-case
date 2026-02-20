package com.technoly.application.service;

import com.technoly.domain.model.FlightDto;
import com.technoly.domain.model.FlightSearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FlightFilterService Unit Testi
 *
 * Pure function karakteri sayesinde test etmek son derece kolaydır:
 * - Dış bağımlılık yok (Spring context gereksiz)
 * - Her test bağımsız: girdi → çıktı doğrulama
 *
 * Test senaryoları:
 * - Her filtre türü için ayrı test (single responsibility)
 * - Null filtre = filtre yok davranışı
 * - Sınır değerleri (boundary testing): tam eşit, bir üst, bir alt
 * - Birden fazla aktif filtre (composable AND semantiği)
 */
@DisplayName("FlightFilterService Unit Testleri")
class FlightFilterServiceTest {

    private FlightFilterService filterService;

    private LocalDateTime BASE_DEP = LocalDateTime.of(2026, 6, 1, 9, 0);
    private LocalDateTime BASE_ARR = LocalDateTime.of(2026, 6, 1, 12, 0);

    @BeforeEach
    void setUp() {
        filterService = new FlightFilterService();
    }

    // =============================================
    // Fiyat Filtreleri
    // =============================================

    @Test
    @DisplayName("priceMin filtresi: fiyatı düşük uçuşları eler")
    void shouldFilterByPriceMin() {
        List<FlightDto> flights = List.of(
                buildFlight("TK1", BigDecimal.valueOf(100), BASE_DEP, BASE_ARR),
                buildFlight("TK2", BigDecimal.valueOf(300), BASE_DEP, BASE_ARR),
                buildFlight("TK3", BigDecimal.valueOf(500), BASE_DEP, BASE_ARR));

        FlightSearchRequest request = requestWithPriceRange(BigDecimal.valueOf(250), null);
        List<FlightDto> result = filterService.applyFilters(flights, request);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(FlightDto::getPrice)
                .usingElementComparator(BigDecimal::compareTo)
                .doesNotContain(BigDecimal.valueOf(100));
    }

    @Test
    @DisplayName("priceMax filtresi: pahalı uçuşları eler")
    void shouldFilterByPriceMax() {
        List<FlightDto> flights = List.of(
                buildFlight("TK1", BigDecimal.valueOf(100), BASE_DEP, BASE_ARR),
                buildFlight("TK2", BigDecimal.valueOf(300), BASE_DEP, BASE_ARR),
                buildFlight("TK3", BigDecimal.valueOf(500), BASE_DEP, BASE_ARR));

        FlightSearchRequest request = requestWithPriceRange(null, BigDecimal.valueOf(300));
        List<FlightDto> result = filterService.applyFilters(flights, request);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(FlightDto::getPrice)
                .usingElementComparator(BigDecimal::compareTo)
                .doesNotContain(BigDecimal.valueOf(500));
    }

    @Test
    @DisplayName("priceMin ve priceMax birlikte: fiyat aralığı (between)")
    void shouldFilterByPriceRange() {
        List<FlightDto> flights = List.of(
                buildFlight("TK1", BigDecimal.valueOf(100), BASE_DEP, BASE_ARR),
                buildFlight("TK2", BigDecimal.valueOf(250), BASE_DEP, BASE_ARR),
                buildFlight("TK3", BigDecimal.valueOf(400), BASE_DEP, BASE_ARR),
                buildFlight("TK4", BigDecimal.valueOf(600), BASE_DEP, BASE_ARR));

        FlightSearchRequest request = requestWithPriceRange(BigDecimal.valueOf(200), BigDecimal.valueOf(450));
        List<FlightDto> result = filterService.applyFilters(flights, request);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(f -> f.getPrice().intValue())
                .containsExactlyInAnyOrder(250, 400);
    }

    @Test
    @DisplayName("Sınır değeri: priceMin'e tam eşit fiyat dahil edilmeli")
    void shouldIncludeFlightExactlyAtPriceMin() {
        FlightDto exactPrice = buildFlight("TK1", BigDecimal.valueOf(200), BASE_DEP, BASE_ARR);
        FlightSearchRequest request = requestWithPriceRange(BigDecimal.valueOf(200), null);

        List<FlightDto> result = filterService.applyFilters(List.of(exactPrice), request);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Null fiyatlı uçuş fiyat filtresi varken elenmelidir")
    void shouldFilterOutNullPriceWhenPriceFilterActive() {
        FlightDto nullPriceFlight = buildFlight("TK1", null, BASE_DEP, BASE_ARR);
        FlightSearchRequest request = requestWithPriceRange(BigDecimal.valueOf(100), null);

        List<FlightDto> result = filterService.applyFilters(List.of(nullPriceFlight), request);

        assertThat(result).isEmpty();
    }

    // =============================================
    // Kalkış Tarihi Filtreleri
    // =============================================

    @Test
    @DisplayName("departureDateFrom filtresi: erkenci uçuşları eler")
    void shouldFilterByDepartureDateFrom() {
        LocalDateTime morning = LocalDateTime.of(2026, 6, 1, 7, 0);
        LocalDateTime noon = LocalDateTime.of(2026, 6, 1, 12, 0);
        LocalDateTime evening = LocalDateTime.of(2026, 6, 1, 18, 0);

        List<FlightDto> flights = List.of(
                buildFlight("TK1", BigDecimal.valueOf(200), morning, morning.plusHours(3)),
                buildFlight("TK2", BigDecimal.valueOf(300), noon, noon.plusHours(3)),
                buildFlight("TK3", BigDecimal.valueOf(400), evening, evening.plusHours(3)));

        FlightSearchRequest request = FlightSearchRequest.builder()
                .origin("IST").destination("COV")
                .departureDate(LocalDateTime.now().plusDays(1))
                .departureDateFrom(noon)
                .build();

        List<FlightDto> result = filterService.applyFilters(flights, request);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(FlightDto::getDepartureDateTime)
                .doesNotContain(morning);
    }

    @Test
    @DisplayName("departureDateTo filtresi: geç kalkışları eler")
    void shouldFilterByDepartureDateTo() {
        LocalDateTime morning = LocalDateTime.of(2026, 6, 1, 7, 0);
        LocalDateTime evening = LocalDateTime.of(2026, 6, 1, 18, 0);

        List<FlightDto> flights = List.of(
                buildFlight("TK1", BigDecimal.valueOf(200), morning, morning.plusHours(3)),
                buildFlight("TK2", BigDecimal.valueOf(400), evening, evening.plusHours(3)));

        FlightSearchRequest request = FlightSearchRequest.builder()
                .origin("IST").destination("COV")
                .departureDate(LocalDateTime.now().plusDays(1))
                .departureDateTo(LocalDateTime.of(2026, 6, 1, 10, 0))
                .build();

        List<FlightDto> result = filterService.applyFilters(flights, request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDepartureDateTime()).isEqualTo(morning);
    }

    // =============================================
    // Varış Tarihi Filtreleri
    // =============================================

    @Test
    @DisplayName("arrivalDateFrom filtresi: erken varan uçuşları eler")
    void shouldFilterByArrivalDateFrom() {
        LocalDateTime earlyArr = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime lateArr = LocalDateTime.of(2026, 6, 1, 20, 0);

        List<FlightDto> flights = List.of(
                buildFlight("TK1", BigDecimal.valueOf(200), BASE_DEP, earlyArr),
                buildFlight("TK2", BigDecimal.valueOf(300), BASE_DEP, lateArr));

        FlightSearchRequest request = FlightSearchRequest.builder()
                .origin("IST").destination("COV")
                .departureDate(LocalDateTime.now().plusDays(1))
                .arrivalDateFrom(LocalDateTime.of(2026, 6, 1, 15, 0))
                .build();

        List<FlightDto> result = filterService.applyFilters(flights, request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getArrivalDateTime()).isEqualTo(lateArr);
    }

    @Test
    @DisplayName("arrivalDateTo filtresi: geç varışları eler")
    void shouldFilterByArrivalDateTo() {
        LocalDateTime earlyArr = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime lateArr = LocalDateTime.of(2026, 6, 1, 22, 0);

        List<FlightDto> flights = List.of(
                buildFlight("TK1", BigDecimal.valueOf(200), BASE_DEP, earlyArr),
                buildFlight("TK2", BigDecimal.valueOf(300), BASE_DEP, lateArr));

        FlightSearchRequest request = FlightSearchRequest.builder()
                .origin("IST").destination("COV")
                .departureDate(LocalDateTime.now().plusDays(1))
                .arrivalDateTo(LocalDateTime.of(2026, 6, 1, 12, 0))
                .build();

        List<FlightDto> result = filterService.applyFilters(flights, request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getArrivalDateTime()).isEqualTo(earlyArr);
    }

    // =============================================
    // Kenar Durumlar
    // =============================================

    @Test
    @DisplayName("Filtre yok → tüm uçuşlar döner (bypass)")
    void shouldReturnAllFlightsWhenNoFiltersActive() {
        List<FlightDto> flights = List.of(
                buildFlight("TK1", BigDecimal.valueOf(200), BASE_DEP, BASE_ARR),
                buildFlight("TK2", BigDecimal.valueOf(400), BASE_DEP, BASE_ARR));

        FlightSearchRequest request = FlightSearchRequest.builder()
                .origin("IST").destination("COV")
                .departureDate(LocalDateTime.now().plusDays(1))
                .build();

        List<FlightDto> result = filterService.applyFilters(flights, request);

        assertThat(result).hasSize(2);
        assertThat(result).isSameAs(flights); // Aynı referans — Stream overhead yok
    }

    @Test
    @DisplayName("Boş liste → boş döner")
    void shouldReturnEmptyForEmptyInput() {
        FlightSearchRequest request = requestWithPriceRange(BigDecimal.valueOf(100), null);
        List<FlightDto> result = filterService.applyFilters(new ArrayList<>(), request);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("hasActiveFilters: filtre yok → false")
    void hasActiveFilters_noFilters_returnsFalse() {
        FlightSearchRequest request = FlightSearchRequest.builder()
                .origin("IST").destination("COV")
                .departureDate(LocalDateTime.now().plusDays(1))
                .build();
        assertThat(request.hasActiveFilters()).isFalse();
    }

    @Test
    @DisplayName("hasActiveFilters: priceMin set → true")
    void hasActiveFilters_withPrice_returnsTrue() {
        FlightSearchRequest request = FlightSearchRequest.builder()
                .origin("IST").destination("COV")
                .departureDate(LocalDateTime.now().plusDays(1))
                .priceMin(BigDecimal.valueOf(100))
                .build();
        assertThat(request.hasActiveFilters()).isTrue();
    }

    // ---- Helpers ----

    private FlightDto buildFlight(String flightNumber, BigDecimal price,
            LocalDateTime dep, LocalDateTime arr) {
        return FlightDto.builder()
                .flightNumber(flightNumber)
                .origin("IST")
                .destination("COV")
                .departureDateTime(dep)
                .arrivalDateTime(arr)
                .price(price)
                .provider("TEST")
                .build();
    }

    private FlightSearchRequest requestWithPriceRange(BigDecimal min, BigDecimal max) {
        return FlightSearchRequest.builder()
                .origin("IST")
                .destination("COV")
                .departureDate(LocalDateTime.now().plusDays(1))
                .priceMin(min)
                .priceMax(max)
                .build();
    }
}
