-- O reaper (timeout de pedidos PENDING) varre so os pendentes mais antigos.
CREATE INDEX idx_orders_pending ON orders (created_at) WHERE status = 'PENDING';
