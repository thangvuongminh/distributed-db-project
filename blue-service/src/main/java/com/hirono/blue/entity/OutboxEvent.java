package com.hirono.blue.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "aggregate_id", nullable = false, length = 64)
  private String aggregateId;

  @Column(name = "event_type", nullable = false, length = 20)
  private String eventType;  // CREATE, UPDATE, DELETE

  @Column(columnDefinition = "JSON", nullable = false)
  private String payload;

  @Column(nullable = false, length = 20)
  @Builder.Default
  private String origin = "BLUE";

  @Column(nullable = false)
  @Builder.Default
  private Boolean processed = false;

  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
  }
}