-- Busca por nome: LIKE '%texto%' (sobre lower(name)) nao usa indice comum. O indice trigram (GIN) usa.
-- pg_trgm e uma extensao "confiavel" no PostgreSQL 13+: o dono do banco pode cria-la.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_products_name_trgm ON products USING gin (lower(name) gin_trgm_ops);
