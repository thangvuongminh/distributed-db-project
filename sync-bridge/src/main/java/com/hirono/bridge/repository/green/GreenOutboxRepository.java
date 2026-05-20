package com.hirono.bridge.repository.green;

import com.hirono.bridge.entity.green.GreenOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GreenOutboxRepository extends JpaRepository<GreenOutboxEvent, Long> {
  List<GreenOutboxEvent> findTop50ByProcessedFalseOrderByCreatedAtAsc();
}