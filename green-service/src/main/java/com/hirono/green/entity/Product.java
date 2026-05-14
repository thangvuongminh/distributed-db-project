package com.hirono.green.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

  @Id
  @Column(length = 36)
  private String id;  // UUID

  @Column(nullable = false, length = 255)
  private String title;

  @Column(name = "price_amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal priceAmount;

  @Column(nullable = false, length = 3)
  @Builder.Default
  private String currency = "USD";

  @Column(name = "discount_percentage", precision = 5, scale = 2)
  private BigDecimal discountPercentage;

  @Column(precision = 3, scale = 2)
  private BigDecimal rating;

  @Column(name = "stock_quantity", nullable = false)
  private Integer stockQuantity;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "category_id", nullable = false)
  private Category category;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "brand_id")
  private Brand brand;

  @Column(length = 50, unique = true)
  private String sku;

  @Enumerated(EnumType.STRING)
  @Column(name = "availability_status")
  @Builder.Default
  private AvailabilityStatus availabilityStatus = AvailabilityStatus.IN_STOCK;

  @Column(columnDefinition = "JSON")
  private String dimensions;

  @Column(columnDefinition = "JSON")
  private String metadata;

  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @PrePersist
  protected void onCreate() {
    if (id == null) id = UUID.randomUUID().toString();
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }

  public enum AvailabilityStatus {
    IN_STOCK("In Stock"),
    LOW_STOCK("Low Stock"),
    OUT_OF_STOCK("Out of Stock");

    private final String dbValue;

    AvailabilityStatus(String dbValue) { this.dbValue = dbValue; }

    public String getDbValue() { return dbValue; }
  }
}