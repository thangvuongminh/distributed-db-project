package com.hirono.blue.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hirono.blue.dto.ProductDto;
import com.hirono.blue.entity.OutboxEvent;
import com.hirono.blue.entity.Product;
import com.hirono.blue.repository.OutboxRepository;
import com.hirono.blue.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

  private final ProductRepository productRepository;
  private final OutboxRepository outboxRepository;
  private final ObjectMapper objectMapper = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  public List<Product> getAll() {
    return productRepository.findAll();
  }

  public Product getById(Long id) {
    return productRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Product not found: " + id));
  }

  @Transactional
  public Product create(ProductDto dto) {
    Product product = Product.builder()
        .title(dto.getTitle())
        .description(dto.getDescription())
        .price(dto.getPrice())
        .discountPercentage(dto.getDiscountPercentage())
        .rating(dto.getRating())
        .stock(dto.getStock())
        .category(dto.getCategory())
        .brand(dto.getBrand())
        .sku(dto.getSku())
        .availabilityStatus(dto.getAvailabilityStatus())
        .build();

    Product saved = productRepository.save(product);
    publishOutboxEvent("CREATE", saved);
    log.info("Created product [BLUE]: id={}", saved.getId());
    return saved;
  }

  @Transactional
  public Product update(Long id, ProductDto dto) {
    Product product = getById(id);
    product.setTitle(dto.getTitle());
    product.setDescription(dto.getDescription());
    product.setPrice(dto.getPrice());
    product.setDiscountPercentage(dto.getDiscountPercentage());
    product.setRating(dto.getRating());
    product.setStock(dto.getStock());
    product.setCategory(dto.getCategory());
    product.setBrand(dto.getBrand());
    product.setSku(dto.getSku());
    product.setAvailabilityStatus(dto.getAvailabilityStatus());

    Product updated = productRepository.save(product);
    publishOutboxEvent("UPDATE", updated);
    log.info("Updated product [BLUE]: id={}", updated.getId());
    return updated;
  }

  @Transactional
  public void delete(Long id) {
    Product product = getById(id);
    productRepository.delete(product);
    publishOutboxEvent("DELETE", product);
    log.info("Deleted product [BLUE]: id={}", id);
  }

  private void publishOutboxEvent(String eventType, Product product) {
    try {
      String payload = objectMapper.writeValueAsString(product);
      OutboxEvent event = OutboxEvent.builder()
          .aggregateId(String.valueOf(product.getId()))
          .eventType(eventType)
          .payload(payload)
          .origin("BLUE")
          .processed(false)
          .build();
      outboxRepository.save(event);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize product for outbox", e);
      throw new RuntimeException("Outbox serialization failed", e);
    }
  }
}