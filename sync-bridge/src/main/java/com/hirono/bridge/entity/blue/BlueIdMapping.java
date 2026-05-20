package com.hirono.bridge.entity.blue;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "id_mapping")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlueIdMapping {

  @Id
  @Column(name = "blue_id")
  private Long blueId;

  @Column(name = "green_uuid", nullable = false, unique = true)
  private String greenUuid;
}