package com.hirono.bridge.entity.green;

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
public class GreenOutboxEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "aggregate_id", nullable = false)
  private String aggregateId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(columnDefinition = "JSON", nullable = false)
  private String payload;

  @Column(nullable = false)
  private String origin;

  @Column(nullable = false)
  private Boolean processed;

  @Column(name = "created_at")
  private LocalDateTime createdAt;
}