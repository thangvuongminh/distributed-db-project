package com.hirono.bridge.repository.blue;

import com.hirono.bridge.entity.blue.BlueOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BlueOutboxRepository extends JpaRepository<BlueOutboxEvent, Long> {
  List<BlueOutboxEvent> findTop50ByProcessedFalseOrderByCreatedAtAsc();
}