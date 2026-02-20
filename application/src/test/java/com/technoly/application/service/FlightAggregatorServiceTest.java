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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * FlightAggregatorService Unit Testi
 *
 * Test stratejisi:
 * - Her FlightProviderPort implementasyonu mock'lanır
 * - Paralel çağrı mantığı doğrulanır (her provider bir kez çağrılır)
 * - Birleştirme sonrası liste boyutu kontrol edilir
 * - Provider hata verirse diğerini etkilemediği test edilir
 */
@ExtendWith(MockitoExtension.class)
// LENIENT: setUp() içindeki getProviderName() stubbing'i bazı testlerde
// kullanılmyor
// (ortak kurulum mesajı — sadece ilgili testlerde gerçekleşir)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FlightAggregatorService Unit Testleri")
class FlightAggregatorServiceTest {

    @Mock
    private FlightProviderPort providerA;

    @Mock
    private FlightProviderPort providerB;

    private FlightAggregatorService aggregatorService;

    private FlightSearchRequest testRequest;

    @BeforeEach
    void setUp() {
        // FlightFilterService: pure function, gerçek instance kullanılır (mock'a gerek
        // yok)
        FlightFilterService filterService = new FlightFilterService();
        // İki mock provider ile servis oluştur (Strategy Pattern)
        aggregatorService = new FlightAggregatorService(List.of(providerA, providerB), filterService);

        testRequest = FlightSearchRequest.builder()
                .origin("IST")
                .destination("COV")
                .departureDate(LocalDateTime.now().plusDays(30))
                .build();

        // Provider adları mock'larda ayarlanır
        when(providerA.getProviderName()).thenReturn("PROVIDER_A");
        when(providerB.getProviderName()).thenReturn("PROVIDER_B");
    }

    @Test
    @DisplayName("Her iki sağlayıcıya tam olarak bir kez çağrı yapılır")
    void shouldCallBothProvidersOnce() {
        // GIVEN
        when(providerA.searchFlights(testRequest)).thenReturn(new ArrayList<>());
        when(providerB.searchFlights(testRequest)).thenReturn(new ArrayList<>());

        // WHEN
        aggregatorService.searchAllFlights(testRequest);

        // THEN: Her provider tam 1 kez çağrılmış olmalı
        verify(providerA, times(1)).searchFlights(testRequest);
        verify(providerB, times(1)).searchFlights(testRequest);
    }

    @Test
    @DisplayName("Her iki sağlayıcıdan gelen sonuçlar birleştirilir")
    void shouldMergeFlightsFromBothProviders() {
        // GIVEN: ProviderA 3, ProviderB 4 uçuş döner
        List<FlightDto> providerAFlights = createFlights(3, "PROVIDER_A");
        List<FlightDto> providerBFlights = createFlights(4, "PROVIDER_B");

        when(providerA.searchFlights(testRequest)).thenReturn(providerAFlights);
        when(providerB.searchFlights(testRequest)).thenReturn(providerBFlights);

        // WHEN
        List<FlightDto> result = aggregatorService.searchAllFlights(testRequest);

        // THEN: 3 + 4 = 7 uçuş toplam
        assertThat(result).hasSize(7);
    }

    @Test
    @DisplayName("Bir sağlayıcı boş liste döndürdüğünde diğerinin sonuçları döner")
    void shouldReturnResultsWhenOneProviderReturnsEmpty() {
        // GIVEN: ProviderA boş, ProviderB 5 uçuş
        when(providerA.searchFlights(testRequest)).thenReturn(new ArrayList<>());
        when(providerB.searchFlights(testRequest)).thenReturn(createFlights(5, "PROVIDER_B"));

        // WHEN
        List<FlightDto> result = aggregatorService.searchAllFlights(testRequest);

        // THEN: Sadece ProviderB'nin 5 uçuşu döner
        assertThat(result).hasSize(5);
        assertThat(result).allMatch(f -> "PROVIDER_B".equals(f.getProvider()));
    }

    @Test
    @DisplayName("Bir sağlayıcı exception fırlattığında diğerinin sonuçları döner")
    void shouldReturnOtherProviderResultsWhenOneThrowsException() {
        // GIVEN: ProviderA RuntimeException, ProviderB normal çalışıyor
        when(providerA.searchFlights(testRequest)).thenThrow(new RuntimeException("ProviderA is down"));
        when(providerB.searchFlights(testRequest)).thenReturn(createFlights(3, "PROVIDER_B"));

        // WHEN: Exception fırlansa bile uygulama çökmemeli
        List<FlightDto> result = aggregatorService.searchAllFlights(testRequest);

        // THEN: ProviderB'nin uçuşları döner
        assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("Her iki sağlayıcı boş döndürürdüğünde boş liste döner")
    void shouldReturnEmptyListWhenBothProvidersReturnEmpty() {
        when(providerA.searchFlights(testRequest)).thenReturn(new ArrayList<>());
        when(providerB.searchFlights(testRequest)).thenReturn(new ArrayList<>());

        List<FlightDto> result = aggregatorService.searchAllFlights(testRequest);

        assertThat(result).isNotNull().isEmpty();
    }

    // ---- Helper ----
    private List<FlightDto> createFlights(int count, String provider) {
        List<FlightDto> flights = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LocalDateTime dep = LocalDateTime.now().plusDays(30).withHour(8 + i);
            flights.add(FlightDto.builder()
                    .flightNumber("XX" + (1000 + i))
                    .origin("IST")
                    .destination("COV")
                    .departureDateTime(dep)
                    .arrivalDateTime(dep.plusHours(3))
                    .price(BigDecimal.valueOf(200 + (i * 50)))
                    .provider(provider)
                    .build());
        }
        return flights;
    }
}
