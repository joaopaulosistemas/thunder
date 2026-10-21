-- Quem fez o pedido (claim preferred_username do token). Pedidos antigos ficam como 'anonymous'.
ALTER TABLE orders ADD COLUMN customer VARCHAR(255) NOT NULL DEFAULT 'anonymous';

CREATE INDEX idx_orders_customer ON orders (customer);
