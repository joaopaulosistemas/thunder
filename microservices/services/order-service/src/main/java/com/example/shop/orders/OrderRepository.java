package com.example.shop.orders;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findTop20ByOrderByIdDesc();

    List<Order> findTop20ByCustomerOrderByIdDesc(String customer);

    long countByStatus(OrderStatus status);

    @Query("select coalesce(sum(o.totalCents), 0) from Order o where o.status = com.example.shop.orders.OrderStatus.CONFIRMED")
    long confirmedRevenueCents();

    /**
     * Pedidos PENDING mais antigos que o limite, com a linha travada. "skip locked": cada replica do
     * servico pega um lote diferente, entao dois reapers nunca recusam o mesmo pedido.
     */
    @Query(value = "select * from orders where status = 'PENDING' and created_at < :cutoff "
            + "order by id limit :limit for update skip locked", nativeQuery = true)
    List<Order> lockExpiredPending(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
