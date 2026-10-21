package com.example.shop.orders.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findTop20ByOrderByIdDesc();

    List<Order> findTop20ByCustomerOrderByIdDesc(String customer);

    @Query("select coalesce(sum(o.totalCents), 0) from Order o")
    long totalRevenueCents();
}
