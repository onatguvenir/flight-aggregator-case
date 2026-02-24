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
 * CheapestFlightService Unit Test
 *
 * Unit test strategy:
 * - @ExtendWith(MockitoExtension): Spring context is not loaded (fast)
 * - Dependencies are mocked using Mockito
 * - Each test verifies a single behavior (Single Responsibility)
 *
 * AssertJ: Fluent assertion API (more readable than JUnit's assertSame)
 * - assertThat(...).hasSize(3).extracting(...).contains(...)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CheapestFlightService Unit Tests")
class CheapestFlightServiceTest {

        @Mock
        private FlightAggregatorService flightAggregatorService;

        private CheapestFlightService cheapestFlightService;

        private FlightSearchRequest testRequest;

        @BeforeEach
        void setUp() {
                // FlightFilterService: pure function, real instance is used
                FlightFilterService filterService = new FlightFilterService();
                cheapestFlightService = new CheapestFlightService(flightAggregatorService, filterService);

                testRequest = FlightSearchRequest.builder()
                                .origin("IST")
                                .destination("COV")
                                .departureDate(LocalDateTime.now().plusDays(30))
                                .build();
        }

        @Test
        @DisplayName("Selects the cheapest flight from flights with the same flight number, origin/destination, and date")
        void shouldSelectCheapestFlightFromSameGroup() {
                // GIVEN: 3 flights belonging to the same group (different prices)
                LocalDateTime dep = LocalDateTime.now().plusDays(30).withHour(9);
                LocalDateTime arr = dep.plusHours(3);

                FlightDto expensiveFlight = buildFlight("TK1001", "IST", "COV", dep, arr, BigDecimal.valueOf(500));
                FlightDto cheapFlight = buildFlight("TK1001", "IST", "COV", dep, arr, BigDecimal.valueOf(200));
                FlightDto mediumFlight = buildFlight("TK1001", "IST", "COV", dep, arr, BigDecimal.valueOf(350));

                when(flightAggregatorService.searchAllFlights(testRequest))
                                .thenReturn(List.of(expensiveFlight, cheapFlight, mediumFlight));

                // WHEN: Cheapest flights are searched
                List<FlightDto> result = cheapestFlightService.findCheapestFlights(testRequest);

                // THEN: Only 1 flight returns (cheapest: 200)
                assertThat(result).hasSize(1);
                assertThat(result.get(0).getPrice()).isEqualByComparingTo(BigDecimal.valueOf(200));
        }

        @Test
        @DisplayName("Flights belonging to different groups are returned separately")
        void shouldReturnOneFlightPerGroup() {
                // GIVEN: 2 different groups, 2 flights each
                LocalDateTime dep1 = LocalDateTime.now().plusDays(30).withHour(9);
                LocalDateTime arr1 = dep1.plusHours(3);
                LocalDateTime dep2 = LocalDateTime.now().plusDays(30).withHour(12);
                LocalDateTime arr2 = dep2.plusHours(3);

                // Group 1: TK1001, 09:00
                FlightDto group1_expensive = buildFlight("TK1001", "IST", "COV", dep1, arr1, BigDecimal.valueOf(400));
                FlightDto group1_cheap = buildFlight("TK1001", "IST", "COV", dep1, arr1, BigDecimal.valueOf(250));

                // Group 2: TK1002, 12:00
                FlightDto group2_expensive = buildFlight("TK1002", "IST", "COV", dep2, arr2, BigDecimal.valueOf(600));
                FlightDto group2_cheap = buildFlight("TK1002", "IST", "COV", dep2, arr2, BigDecimal.valueOf(300));

                when(flightAggregatorService.searchAllFlights(testRequest))
                                .thenReturn(List.of(group1_expensive, group1_cheap, group2_expensive, group2_cheap));

                // WHEN
                List<FlightDto> result = cheapestFlightService.findCheapestFlights(testRequest);

                // THEN: 2 groups → 2 flights return
                assertThat(result).hasSize(2);
                // Must contain the cheapest of each group
                assertThat(result)
                                .extracting(FlightDto::getPrice)
                                .usingElementComparator(BigDecimal::compareTo)
                                .containsExactlyInAnyOrder(BigDecimal.valueOf(250), BigDecimal.valueOf(300));
        }

        @Test
        @DisplayName("Returns results sorted by price in ascending order")
        void shouldReturnResultsSortedByPrice() {
                // GIVEN: 3 different flight groups with different prices
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

                // THEN: Sorted from cheapest to most expensive
                assertThat(result).hasSize(3);
                assertThat(result.get(0).getPrice()).isEqualByComparingTo(BigDecimal.valueOf(200));
                assertThat(result.get(1).getPrice()).isEqualByComparingTo(BigDecimal.valueOf(450));
                assertThat(result.get(2).getPrice()).isEqualByComparingTo(BigDecimal.valueOf(800));
        }

        @Test
        @DisplayName("Returns an empty list when there are no flights (not null)")
        void shouldReturnEmptyListWhenNoFlights() {
                // GIVEN: Provider returns an empty list
                when(flightAggregatorService.searchAllFlights(testRequest))
                                .thenReturn(new ArrayList<>());

                // WHEN
                List<FlightDto> result = cheapestFlightService.findCheapestFlights(testRequest);

                // THEN: Not null, empty list
                assertThat(result).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("Provider mock is called once")
        void shouldCallAggregatorServiceOnce() {
                when(flightAggregatorService.searchAllFlights(testRequest)).thenReturn(new ArrayList<>());

                cheapestFlightService.findCheapestFlights(testRequest);

                // FlightAggregatorService must be called exactly 1 time
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
