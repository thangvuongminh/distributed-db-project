package com.hirono.blue.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDto {
  private Long id;
  private String title;
  private String description;
  private BigDecimal price;
  private BigDecimal discountPercentage;
  private BigDecimal rating;
  private Integer stock;
  private String category;
  private String brand;
  private String sku;
  private String availabilityStatus;
}