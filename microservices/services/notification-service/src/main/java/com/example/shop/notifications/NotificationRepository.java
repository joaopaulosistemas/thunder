package com.example.shop.notifications;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByOrderByIdDesc();

    List<Notification> findTop50ByCustomerOrderByIdDesc(String customer);
}
