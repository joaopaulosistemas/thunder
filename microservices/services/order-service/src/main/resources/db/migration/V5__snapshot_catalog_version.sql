-- Versao do produto no catalogo, para ignorar um UPSERTED mais velho que a copia local.
ALTER TABLE product_snapshot ADD COLUMN catalog_version BIGINT NOT NULL DEFAULT 0;
