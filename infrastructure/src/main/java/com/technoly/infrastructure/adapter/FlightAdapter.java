package com.technoly.infrastructure.adapter;

import com.technoly.domain.model.FlightDto;
import com.technoly.domain.model.FlightSearchRequest;
import com.technoly.domain.port.FlightProviderPort;
import com.technoly.domain.port.FlightSearchPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * FlightSearchPort implementasyonu: provider orkestrasyonu yapan adapter.
 *
 * Sorumluluk:
 * - Spring tarafından inject edilen tüm {@link FlightProviderPort} implementasyonlarını
 *   (ör. ProviderA, ProviderB) tek bir "arama" operasyonu altında birleştirir.
 * - Provider çağrılarını paralel çalıştırır ve sonuçları tek listede toplar.
 *
 * Tasarım notları:
 * - {@code List<FlightProviderPort>} injection → yeni bir provider eklemek için
 *   sadece yeni bir {@code @Component} implementasyon eklemek yeterli (OCP).
 * - "Kısmi başarı" yaklaşımı: Bir provider hata verirse diğer provider sonuçları
 *   yine de dönülebilir; bu yüzden hatada boş listeye düşülür.
 * - Timeout: Provider'ın takılı kalması tüm isteği bloke etmesin diye her
 *   provider çağrısına süre sınırı uygulanır.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class FlightAdapter implements FlightSearchPort {

    private final List<FlightProviderPort> flightProviders;
    /**
     * Provider çağrılarını paralelleştirmek için kullanılan thread pool.
     *
     * Not: Bu proje örneğinde adapter kendi executor'ını oluşturuyor.
     * Üretim sistemlerinde genellikle {@code @Bean TaskExecutor} üzerinden yönetmek,
     * shutdown/lifecycle yönetimini Spring'e bırakmak daha sağlıklıdır.
     */
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    @Override
    public List<FlightDto> searchAllFlights(FlightSearchRequest request) {
        log.info("Paralel SOAP araması (Adapter): {} → {}", request.getOrigin(), request.getDestination());

        List<CompletableFuture<List<FlightDto>>> futures = flightProviders.stream()
                .map(provider -> CompletableFuture
                        .supplyAsync(() -> searchWithProvider(provider, request), executorService)
                        // Provider bazında üst süre sınırı: bu süre aşılırsa Exception'a düşer
                        .orTimeout(10, TimeUnit.SECONDS)
                        .exceptionally(ex -> {
                            // Bu noktada "kısmi başarı" için hata yutulur ve boş listeye düşülür.
                            // Böylece diğer provider'ların sonuçları yine de dönebilir.
                            log.error("[{}] hata: {}", provider.getProviderName(), ex.getMessage());
                            return new ArrayList<>();
                        }))
                .toList();

        // join(): tüm futures tamamlanana kadar bekler (ya sonuç ya da boş liste)
        return futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .toList();
    }

    private List<FlightDto> searchWithProvider(FlightProviderPort provider, FlightSearchRequest request) {
        try {
            // Provider implementasyonu kendi içinde Resilience4j (retry/cb/bulkhead) uyguluyor olabilir.
            // Burada amaç: tek provider hatasının tüm akışı bozmamasını sağlamak.
            log.info("searchWithProvider Request: {}", request.toString());
            return provider.searchFlights(request);
        } catch (Exception e) {
            log.error("[{}] Beklenmeyen hata: {} - Request: {}", provider.getProviderName(), e.getMessage(),request.toString());
            return new ArrayList<>();
        }
    }
}
