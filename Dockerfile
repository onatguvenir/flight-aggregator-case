# =====================================================================
# Flight Aggregator Dockerfile — Multi-stage build
# =====================================================================

# Stage 1: Build — Maven + JDK 17
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder

WORKDIR /app

# Modüler yapı: her modülün pom.xml'ini ayrı kopyala
# (Docker layer caching: kaynak değişince sadece o katman yeniden build edilir)
COPY pom.xml .
COPY domain/pom.xml domain/
COPY infrastructure/pom.xml infrastructure/
COPY application/pom.xml application/
COPY api/pom.xml api/

# Bağımlılıkları önden indir → sonraki build'lerde bu katman cache'den gelir
RUN mvn dependency:go-offline -q

# Kaynak kodları kopyala
COPY domain/src domain/src
COPY infrastructure/src infrastructure/src
COPY application/src application/src
COPY api/src api/src

# Sadece api modülünü ve bağımlılıklarını (-am) paketle
# DskipTests: build sırasında DB/Redis yoktur
RUN mvn clean package -DskipTests -pl api -am -q

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
COPY --from=builder /app/api/target/api-*.jar app.jar

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
