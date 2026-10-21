package com.example.shop.common.inbox;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    @Modifying
    @Query("delete from ProcessedEvent p where p.processedAt < :before")
    int deleteProcessedBefore(@Param("before") Instant before);
}
