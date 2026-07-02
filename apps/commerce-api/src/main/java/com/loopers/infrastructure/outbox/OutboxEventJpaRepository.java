package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByStatusOrderByIdAsc(OutboxEvent.Status status, Pageable pageable);
}
