## Flight Aggregator – Proje Genel Yapısı

Bu proje, **modüler monolit** olarak tasarlanmış bir uçuş toplama (flight aggregator) uygulamasıdır. Tek bir deployable artifact (`api` modülü) vardır, ancak iş kuralları ve altyapı katmanları ayrı Maven modüllerine ayrılmıştır.

- **Ana teknoloji yığını**: Spring Boot 3.2, Java 17, Maven
- **Mimari stil**: Modüler monolit (domain → infrastructure → application → api)
- **Entegrasyonlar**: SOAP uçuş sağlayıcıları, Redis, PostgreSQL, Prometheus, ELK, ELK/Logstash, Prometheus/Grafana

---

## Tech Stack ve Kullanılan Kütüphaneler

- **Dil & Platform**
  - **Java 17**
  - **Spring Boot 3.2.x** (`spring-boot-starter-parent`)
  - **Maven** (multi-module proje yapısı)

- **Core Spring Starter’lar**
  - **`spring-boot-starter-web`**: REST API ve servlet stack
  - **`spring-boot-starter-validation`**: Bean Validation (Hibernate Validator)
  - **`spring-boot-starter-actuator`**: health, metrics, readiness/liveness endpoint’leri
  - **`spring-boot-starter-security`**: HTTP security filter chain
  - **`spring-boot-starter-oauth2-resource-server`**: JWT bearer token doğrulama
  - **`spring-boot-starter-test`**: JUnit 5, Spring Test, AssertJ vb. için test starter’ı

- **Gözlemlenebilirlik & Monitoring**
  - **Micrometer Prometheus Registry** (`micrometer-registry-prometheus`): Prometheus ile metric toplama
  - **Spring Boot Actuator**: `/actuator` endpoint’leri
  - **Prometheus & Grafana**: `docker-compose` ile ayağa kaldırılan monitoring stack

- **API Dokümantasyonu**
  - **SpringDoc OpenAPI** (`springdoc-openapi-starter-webmvc-ui`): OpenAPI 3 + Swagger UI

- **Dayanıklılık (Resilience)**
  - **Resilience4j BOM** (`resilience4j-bom`): circuit breaker, retry, rate limiter vb. için versiyon yönetimi

- **Veri Tabanı & Migrasyon**
  - **PostgreSQL** (docker-compose ile)
  - **Flyway** + **`flyway-database-postgresql`**: veritabanı migrasyonları ve PostgreSQL eklentisi

- **Cache & Messaging**
  - **Redis** (docker-compose ile)
  - `RedisConfig` üzerinden Spring ile entegrasyon

- **SOAP & Entegrasyon**
  - Spring Web Services / SOAP client konfigürasyonları (`SoapClientConfig`)
  - `FlightProviderA` / `FlightProviderB` modüllerinde SOAP endpoint ve XSD tanımları (`flights.xsd`)

- **Logging & ELK**
  - **Logback** (Spring Boot default logging)
  - **Logstash Logback Encoder** (`logstash-logback-encoder`): JSON formatlı loglar, ELK ile entegrasyon
  - ELK stack (ElasticSearch, Logstash, Kibana) için `docker-compose` tanımı

- **Mapping & Boilerplate Azaltma**
  - **Lombok** (`lombok`, `lombok-mapstruct-binding`): getter/setter, constructor, logger vb. anotasyonlar
  - **MapStruct** (`mapstruct`, `mapstruct-processor`): DTO ↔ domain mapping (compile-time, reflection’sız)

- **Test & Mocking**
  - **JUnit 5**: ana test framework’ü
  - **WireMock Standalone** (`wiremock-standalone`): dış provider’ları mock etmek için
  - **Spring Security Test**: güvenli endpoint’ler için test desteği
  - **H2 Database**: in-memory veritabanı ile integration testleri
  - **JaCoCo Maven Plugin** (`jacoco-maven-plugin`): coverage ölçümü ve quality gate

- **Build & Plugin’ler**
  - **spring-boot-maven-plugin**: yalnızca `api` modülünde executable fat JAR üretir
  - **maven-compiler-plugin**: Java 17 kaynak/target, Lombok & MapStruct annotation processing

---

## Modüler Mimari

Kök `pom.xml`, tüm alt modülleri ve ortak bağımlılıkları yönetir:

- **`domain`**
- **`infrastructure`**
- **`application`**
- **`api`**
- **`FlightProviderA`**
- **`FlightProviderB`**

Bağımlılık akışı şu şekildedir:

```text
domain  →  infrastructure  →  application  →  api
```

`FlightProviderA` ve `FlightProviderB` modülleri ise dış dünyadaki SOAP tabanlı uçuş servislerini temsil eden örnek provider projeleridir.

---

## Kök Proje (`flight-aggregator`)

- **`pom.xml`**: Parent POM
  - Tüm modülleri tanımlar.
  - Java 17, encoding, plugin ve kütüphane versiyonlarını merkezi olarak yönetir.
  - `dependencyManagement` ile:
    - `domain`, `infrastructure`, `application` modüllerinin versiyonları
    - Resilience4j, SpringDoc, Flyway, MapStruct, Lombok, WireMock
  - `build` bölümünde:
    - `spring-boot-maven-plugin` (pluginManagement)
    - `maven-compiler-plugin` (Java 17, Lombok + MapStruct annotation processor’leri)
    - `jacoco-maven-plugin` (coverage raporu ve minimum threshold kontrolleri)
- **`docker-compose.yml`**:
  - PostgreSQL, Redis, Prometheus, Grafana, ELK gibi dış servisleri ayağa kaldırmak için kullanılır (adı ve varlığı bu amaçla kullanıldığını gösterir).
- **`.env`, `.env.qa`, `.env.prod`**:
  - Farklı ortamlar için environment değişkenlerini tanımlamak için kullanılır.

---

## Domain Modülü (`domain`)

**Amaç**: Saf iş kuralları ve domain modelini içerir. Framework bağımlılığı minimumda tutulur.

Örnek paketler / sınıflar:

- `com.technoly.domain.model`
  - `FlightSearchRequest`
  - `FlightSearchResponse`
  - `FlightDto`
- `com.technoly.domain.port`
  - `FlightSearchPort` (application/infrastructure katmanlarına doğru dependency inversion noktası)

Bu modül, veri tabanı, HTTP, SOAP vb. detayları bilmez; sadece uçuş arama ile ilgili core iş kurallarını içerir.

---

## Infrastructure Modülü (`infrastructure`)

**Amaç**: Dış sistemler ve teknik detaylardan sorumludur.

Örnek bileşenler:

- **HTTP/SOAP Client’lar**
  - `com.technoly.infrastructure.client.FlightProviderAClient`
  - `com.technoly.infrastructure.client.FlightProviderBClient`
  - Ortak davranış için `AbstractClient`
- **Flight Adapter**
  - `com.technoly.infrastructure.adapter.FlightAdapter`
  - Farklı provider formatlarını domain modeline dönüştürür.
- **Persistence / Logging**
  - `com.technoly.infrastructure.persistence.entity.ApiLogEntity`
- **Konfigürasyon**
  - `com.technoly.infrastructure.config.RedisConfig`
  - `com.technoly.infrastructure.config.SoapClientConfig`

Testler:

- `src/test/java/com/technoly/infrastructure/...`
  - `FlightAdapterTest`
  - `FlightProviderAClientTest`, `FlightProviderBClientTest`

Bu modül, domain’de tanımlı port’ları implement eder ve dış dünyaya konuşan tüm detayları kapsar.

---

## Application Modülü (`application`)

**Amaç**: Use case / servis katmanıdır. Domain kurallarını kullanarak iş akışlarını orkestre eder.

Örnek servisler:

- `com.technoly.application.service.FlightAggregatorService`
  - Farklı provider’lardan gelen uçuş sonuçlarını toplar.
- `com.technoly.application.service.FlightFilterService`
  - Filtreleme, sıralama, iş kuralı bazlı filtreler.
- `com.technoly.application.service.CheapestFlightService`
  - En ucuz uçuş senaryoları.
- `com.technoly.application.service.ApiLogService`
  - API çağrı loglarını yönetir.

Testler:

- `src/test/java/com/technoly/application/service/...`
  - Service bazlı unit/integration testleri.

Bu katman doğrudan controller bilmez; use case’leri domain nesneleri üzerinden çalıştırır.

---

## API Modülü (`api`)

**Amaç**: Uygulamanın dış dünyaya açılan REST API katmanıdır ve tek deployable Spring Boot uygulamasıdır.

Öne çıkan bileşenler:

- **Spring Boot Uygulaması**
  - `@SpringBootApplication` burada bulunur.
  - Application entry point (main class).
- **REST Controller’lar**
  - `com.technoly.api.controller.FlightSearchController`
    - Uçuş arama endpoint’lerini sağlar.
- **Konfigürasyon**
  - `com.technoly.api.config.OpenApiConfig`
  - `application.yml`, `application-*.yml` (profil bazlı ayarlar)
- **Security**
  - `com.technoly.api.security.SecurityConfig`
  - `RateLimitingFilter` (istek limitleme)
- **Exception Handling**
  - `com.technoly.api.exception.GlobalExceptionHandler`

Testler:

- `SystemIntegrationTest`
- `FlightSearchControllerTest`
- `SecurityConfigTest`
- `GlobalExceptionHandlerTest`

**Bağımlılıklar (özet)**:

- `domain`, `infrastructure`, `application` modüllerinin tamamını tüketir.
- `spring-boot-starter-web`, `spring-boot-starter-validation`
- `spring-boot-starter-actuator`, `micrometer-registry-prometheus`
- `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`
- `springdoc-openapi-starter-webmvc-ui`
- `logstash-logback-encoder`

`spring-boot-maven-plugin` yalnızca bu modülde aktiftir ve executable fat JAR üretir.

---

## Provider Modülleri (`FlightProviderA`, `FlightProviderB`)

**Amaç**: Gerçek hayattaki üçüncü parti uçuş servislerini temsil eden örnek SOAP provider uygulamalarıdır.

Örnek bileşenler:

- `src/main/java/com/flightprovidera/endpoint/FlightEndpoint`
- `src/main/java/com/flightproviderb/endpoint/FlightEndpoint`
- `config/WebServiceConfig` sınıfları ile SOAP endpoint konfigürasyonu
- `flights.xsd` ve JAXB/SOAP ile ilgili şema tanımları
- `application.yml` ve `logback-spring.xml` konfigürasyonları

Bu modüller, `infrastructure` modülündeki SOAP client’ların konuştuğu karşı uçları simüle eder.

---

## Test & Kalite Altyapısı

- **JUnit 5** ve `spring-boot-starter-test` tüm modüllerde ortak.
- **WireMock**:
  - Provider’ları mock etmek için kullanılır (özellikle `api` testlerinde).
- **JaCoCo**:
  - Line ve branch coverage için kalite kapısı:
    - Line coverage ≥ %80
    - Branch coverage ≥ %75
  - `mvn clean verify` ile coverage raporu ve threshold kontrolü yapılır.
- **Hariç tutulan paketler**:
  - `model`, `config`, `*Application`, `soap`, `persistence/entity`, `filter`, `security`

---

## Çalıştırma ve Geliştirme Notları (Özet)

- **Bağımlılıklar**:
  - Java 17
  - Maven
- **Build**:
  - Kökten: `mvn clean install`
- **Uygulamayı çalıştırma**:
  - `api` modülünden: `mvn spring-boot:run` veya oluşturulan JAR üzerinden (`java -jar api-...jar`)
- **Dış servisler**:
  - Gerektiğinde `docker-compose.yml` ile veritabanı, Redis, gözlemlenebilirlik ve log altyapısı ayağa kaldırılabilir.

