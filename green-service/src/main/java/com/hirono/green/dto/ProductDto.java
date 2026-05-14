package com.hirono.green.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDto {
  private String id;
  private String title;
  private BigDecimal priceAmount;
  private String currency;
  private BigDecimal discountPercentage;
  private BigDecimal rating;
  private Integer stockQuantity;
  private String categoryName;
  private String brandName;
  private String sku;
  private String availabilityStatus;  // "IN_STOCK", "LOW_STOCK", "OUT_OF_STOCK"
  private String dimensions;
  private String metadata;
}