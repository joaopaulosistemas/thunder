-- Uma linha por pedido que reservou estoque. Serve para devolver o estoque NO MAXIMO uma vez
-- (compensacao da saga), mesmo que o pedido de devolucao chegue repetido.
CREATE TABLE stock_reservation (
    order_id   BIGINT      PRIMARY KEY,
    product_id BIGINT      NOT NULL,
    quantity   INTEGER     NOT NULL CHECK (quantity > 0),
    released   BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL
);
