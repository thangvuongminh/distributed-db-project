package com.hirono.blue.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal price;

  @Column(name = "discount_percentage", precision = 5, scale = 2)
  private BigDecimal discountPercentage;

  @Column(precision = 3, scale = 2)
  private BigDecimal rating;

  @Column(nullable = false)
  private Integer stock;

  @Column(nullable = false, length = 50)
  private String category;

  @Column(length = 100)
  private String brand;

  @Column(length = 50)
  private String sku;

  @Column(name = "availability_status", length = 50)
  private String availabilityStatus;

  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}