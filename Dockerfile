# =====================================================================
# Flight Aggregator Dockerfile — Multi-stage build
# =====================================================================

# Stage 1: Build — Maven + JDK 17
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder

WORKDIR /app

# Provider A bağımlılığını kopyala ve local m2 repository'sine yükle
COPY FlightProviderA FlightProviderA
RUN mvn -f FlightProviderA/pom.xml clean install -DskipTests

# Provider B bağımlılığını kopyala ve local m2 repository'sine yükle
COPY FlightProviderB FlightProviderB
RUN mvn -f FlightProviderB/pom.xml clean install -DskipTests

# Aggregator projesinin tamamını kopyala
COPY flight-aggregator flight-aggregator

# Aggregator'ı derle (Provider A ve B local repository'de yüklü olduğu için hata vermeden derlenecek)
RUN mvn -f flight-aggregator/pom.xml clean package -DskipTests

# =====================================================================
# Stage 2: Runtime — JRE 17 minimal
# =====================================================================
FROM eclipse-temurin:17-jre-alpine

# curl: /actuator/health healthcheck için zorunlu
RUN apk add --no-cache curl

# Güvenlik: root olmayan kullanıcı
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

WORKDIR /app
COPY --from=builder /app/flight-aggregator/api/target/api-*.jar app.jar

EXPOSE 8080

# JVM Bayrakları:
# -Xss512k              → Thread stack küçültme (çok thread varsa önemli)
# -XX:MaxRAMPercentage  → Konteynere ayrılan RAM'in %75'ini heap için kullan
# -Djava.security.egd   → SecureRandom başlangıç hızlandırma
ENTRYPOINT ["java", \
    "-Xss512k", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
