package com.technoly.application.service;

import com.technoly.domain.model.FlightDto;
import com.technoly.domain.model.FlightSearchRequest;
import com.technoly.domain.port.FlightProviderPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * CheapestFlightService Unit Testi
 *
 * Unit test stratejisi:
 * - @ExtendWith(MockitoExtension): Spring context yüklenmez (hızlı)
 * - Mockito ile bağımlılıklar mock'lanır
 * - Her test tek bir davranışı doğrular (Single Responsibility)
 *
 * AssertJ: Fluent assertion API (JUnit'in assertSame'den daha okunabilir)
 * - assertThat(...).hasSize(3).extracting(...).contains(...)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CheapestFlightService Unit Testleri")
class CheapestFlightServiceTest {

        @Mock
        private FlightAggregatorService flightAggregatorService;

        private CheapestFlightService cheapestFlightService;

        private FlightSearchRequest testRequest;

        @BeforeEach
        void setUp() {
                // FlightFilterService: pure function, gerçek instance kullanılır
                FlightFilterService filterService = new FlightFilterService();
                cheapestFlightService = new CheapestFlightService(flightAggregatorService, filterService);

                testRequest = FlightSearchRequest.builder()
                                .origin("IST")
                                .destination("COV")
                                .departureDate(LocalDateTime.now().plusDays(30))
                                .build();
        }

        @Test
        @DisplayName("Aynı uçuş numarası, kalkış/varış bilgileri ve tarih eşleşen uçuşlardan en ucuzunu seçer")
        void shouldSelectCheapestFlightFromSameGroup() {
                // GIVEN: Aynı gruba ait 3 uçuş (farklı fiyatlar)
                LocalDateTime dep = LocalDateTime.now().plusDays(30).withHour(9);
                LocalDateTime arr = dep.plusHours(3);

                FlightDto expensiveFlight = buildFlight("TK1001", "IST", "COV", dep, arr, BigDecimal.valueOf(500));
                FlightDto cheapFlight = buildFlight("TK1001", "IST", "COV", dep, arr, BigDecimal.valueOf(200));
                FlightDto mediumFlight = buildFlight("TK1001", "IST", "COV", dep, arr, BigDecimal.valueOf(350));

                when(flightAggregatorService.searchAllFlights(testRequest))
                                .thenReturn(List.of(expensiveFlight, cheapFlight, mediumFlight));

                // WHEN: En ucuz uçuşlar aranır
                List<FlightDto> result = cheapestFlightService.findCheapestFlights(testRequest);

                // THEN: Sadece 1 uçuş döner (en ucuz: 200)
                assertThat(result).hasSize(1);
                assertThat(result.get(0).getPrice()).isEqualByComparingTo(BigDecimal.valueOf(200));
        }

        @Test
        @DisplayName("Farklı gruplara ait uçuşlar ayrı ayrı döner")
        void shouldReturnOneFlightPerGroup() {
                // GIVEN: 2 farklı grup, her grup 2 uçuş
                LocalDateTime dep1 = LocalDateTime.now().plusDays(30).withHour(9);
                LocalDateTime arr1 = dep1.plusHours(3);
                LocalDateTime dep2 = LocalDateTime.now().plusDays(30).withHour(12);
                LocalDateTime arr2 = dep2.plusHours(3);

                // Grup 1: TK1001, 09:00
                FlightDto group1_expensive = buildFlight("TK1001", "IST", "COV", dep1, arr1, BigDecimal.valueOf(400));
                FlightDto group1_cheap = buildFlight("TK1001", "IST", "COV", dep1, arr1, BigDecimal.valueOf(250));

                // Grup 2: TK1002, 12:00
                FlightDto group2_expensive = buildFlight("TK1002", "IST", "COV", dep2, arr2, BigDecimal.valueOf(600));
                FlightDto group2_cheap = buildFlight("TK1002", "IST", "COV", dep2, arr2, BigDecimal.valueOf(300));

                when(flightAggregatorService.searchAllFlights(testRequest))
                                .thenReturn(List.of(group1_expensive, group1_cheap, group2_expensive, group2_cheap));

                // WHEN
                List<FlightDto> result = cheapestFlightService.findCheapestFlights(testRequest);

                // THEN: 2 grup → 2 uçuş döner
                assertThat(result).hasSize(2);
                // Her grubun en ucusunu içermeli
                assertThat(result)
                                .extracting(FlightDto::getPrice)
                                .usingElementComparator(BigDecimal::compareTo)
                                .containsExactlyInAnyOrder(BigDecimal.valueOf(250), BigDecimal.valueOf(300));
        }

        @Test
        @DisplayName("Sonuç fiyata göre artan sırayla döner")
        void shouldReturnResultsSortedByPrice() {
                // GIVEN: Farklı fiyatlarda 3 ayrı uçuş grubu
                LocalDateTime dep = LocalDateTime.now().plusDays(30);
                LocalDateTime arr = dep.plusHours(2);

                FlightDto expensive = buildFlight("XQ1001", "IST", "COV", dep.withHour(6), arr.withHour(8),
                                BigDecimal.valueOf(800));
                FlightDto cheap = buildFlight("TK1001", "IST", "COV", dep.withHour(9), arr.withHour(11),
                                BigDecimal.valueOf(200));
                FlightDto medium = buildFlight("PC1001", "IST", "COV", dep.withHour(12), arr.withHour(14),
                                BigDecimal.valueOf(450));

                when(flightAggregatorService.searchAllFlights(testRequest))
                                .thenReturn(List.of(expensive, cheap, medium));

                // WHEN
                List<FlightDto> result = cheapestFlightService.findCheapestFlights(testRequest);

                // THEN: En ucuzdan pahalıya sıralı
                assertThat(result).hasSize(3);
                assertThat(result.get(0).getPrice()).isEqualByComparingTo(BigDecimal.valueOf(200));
                assertThat(result.get(1).getPrice()).isEqualByComparingTo(BigDecimal.valueOf(450));
                assertThat(result.get(2).getPrice()).isEqualByComparingTo(BigDecimal.valueOf(800));
        }

        @Test
        @DisplayName("Hiç uçuş yoksa boş liste döner (null değil)")
        void shouldReturnEmptyListWhenNoFlights() {
                // GIVEN: Provider boş liste döndürür
                when(flightAggregatorService.searchAllFlights(testRequest))
                                .thenReturn(new ArrayList<>());

                // WHEN
                List<FlightDto> result = cheapestFlightService.findCheapestFlights(testRequest);

                // THEN: Null değil, boş liste
                assertThat(result).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("Provider mock bir kez çağrılır")
        void shouldCallAggregatorServiceOnce() {
                when(flightAggregatorService.searchAllFlights(testRequest)).thenReturn(new ArrayList<>());

                cheapestFlightService.findCheapestFlights(testRequest);

                // FlightAggregatorService tam olarak 1 kez çağrılmalı
                verify(flightAggregatorService, times(1)).searchAllFlights(testRequest);
        }

        // ---- Test Helper ----
        private FlightDto buildFlight(String flightNumber, String origin, String destination,
                        LocalDateTime dep, LocalDateTime arr, BigDecimal price) {
                return FlightDto.builder()
                                .flightNumber(flightNumber)
                                .origin(origin)
                                .destination(destination)
                                .departureDateTime(dep)
                                .arrivalDateTime(arr)
                                .price(price)
                                .provider("TEST_PROVIDER")
                                .build();
        }
}
