package com.hirono.bridge.entity.green;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "id_mapping")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GreenIdMapping {

  @Id
  @Column(name = "green_uuid")
  private String greenUuid;

  @Column(name = "blue_id", nullable = false, unique = true)
  private Long blueId;
}