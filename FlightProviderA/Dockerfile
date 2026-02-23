# 1. Build Stage
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Sadece pom.xml kopyalayıp bağımlılıkları indiriyoruz (Docker cache optimizasyonu)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Kaynak kodları kopyalayıp derleme işlemini yapıyoruz
COPY src ./src
RUN mvn clean package -DskipTests

# 2. Run Stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# curl kurulumu (docker-compose healthcheck için)
RUN apk add --no-cache curl

# Uygulama için güvenli bir kullanıcı oluşturuyoruz
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Build aşamasından derlenen jar dosyasını kopyalıyoruz
COPY --from=build /app/target/*.jar app.jar

# Provider A portunu dışa açıyoruz (docker-compose.yml port 8081)
EXPOSE 8081

# Uygulamayı başlatıyoruz
ENTRYPOINT ["java", "-jar", "app.jar"]
