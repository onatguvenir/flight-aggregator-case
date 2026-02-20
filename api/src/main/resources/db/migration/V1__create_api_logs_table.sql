-- =====================================================================
-- V1: api_logs tablosu oluşturma
-- Flyway, bu dosyayı V1__ prefix'i sayesinde ilk migration olarak tanır.
-- İsimlendirme: V{version}__{description}.sql
-- =====================================================================
-- api_logs: REST API çağrılarının request ve response kayıtları.
-- PostgreSQL JSONB kullanıldı:
--   - TEXT'e göre daha hızlı sorgulama (binary format)
--   - JSON path operatörleri ile sorgulama: request->>'origin' = 'IST'
--   - GIN index desteği ile tam metin arama
CREATE TABLE IF NOT EXISTS api_logs (
    id BIGSERIAL PRIMARY KEY,
    endpoint VARCHAR(255) NOT NULL,
    request JSONB NOT NULL,
    response JSONB,
    status_code INTEGER,
    duration_ms BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
-- created_at üzerinde B-Tree index:
-- Zaman aralığı sorgularında (WHERE created_at BETWEEN ... ) performans için
CREATE INDEX IF NOT EXISTS idx_api_logs_created_at ON api_logs (created_at);
-- endpoint üzerinde B-Tree index:
-- Endpoint spesifik sorgularda (WHERE endpoint = '/api/v1/flights/search') performans için
CREATE INDEX IF NOT EXISTS idx_api_logs_endpoint ON api_logs (endpoint);
-- GIN index: JSONB içinde arama yapabilmek için
-- Örnek: SELECT * FROM api_logs WHERE request @> '{"origin": "IST"}'
CREATE INDEX IF NOT EXISTS idx_api_logs_request_gin ON api_logs USING GIN (request);