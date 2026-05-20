package com.hirono.green.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hirono.green.dto.ProductDto;
import com.hirono.green.entity.Brand;
import com.hirono.green.entity.Category;
import com.hirono.green.entity.OutboxEvent;
import com.hirono.green.entity.Product;
import com.hirono.green.repository.BrandRepository;
import com.hirono.green.repository.CategoryRepository;
import com.hirono.green.repository.OutboxRepository;
import com.hirono.green.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;
  private final BrandRepository brandRepository;
  private final OutboxRepository outboxRepository;

  private final ObjectMapper objectMapper = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  public List<Product> getAll() {
    return productRepository.findAll();
  }

  public Product getById(String id) {
    return productRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Product not found: " + id));
  }

  @Transactional
  public Product create(ProductDto dto) {
    Category category = resolveCategory(dto.getCategoryName());
    Brand brand = resolveBrand(dto.getBrandName());

    Product product = Product.builder()
        .title(dto.getTitle())
        .priceAmount(dto.getPriceAmount())
        .currency(dto.getCurrency() != null ? dto.getCurrency() : "USD")
        .discountPercentage(dto.getDiscountPercentage())
        .rating(dto.getRating())
        .stockQuantity(dto.getStockQuantity())
        .category(category)
        .brand(brand)
        .sku(dto.getSku())
        .availabilityStatus(parseStatus(dto.getAvailabilityStatus()))
        .dimensions(dto.getDimensions())
        .metadata(dto.getMetadata())
        .build();

    Product saved = productRepository.save(product);
    publishOutboxEvent("CREATE", saved);
    log.info("Created product [GREEN]: id={}", saved.getId());
    return saved;
  }

  @Transactional
  public Product update(String id, ProductDto dto) {
    Product product = getById(id);
    product.setTitle(dto.getTitle());
    product.setPriceAmount(dto.getPriceAmount());
    if (dto.getCurrency() != null) product.setCurrency(dto.getCurrency());
    product.setDiscountPercentage(dto.getDiscountPercentage());
    product.setRating(dto.getRating());
    product.setStockQuantity(dto.getStockQuantity());
    if (dto.getCategoryName() != null) product.setCategory(resolveCategory(dto.getCategoryName()));
    if (dto.getBrandName() != null) product.setBrand(resolveBrand(dto.getBrandName()));
    product.setSku(dto.getSku());
    product.setAvailabilityStatus(parseStatus(dto.getAvailabilityStatus()));
    product.setDimensions(dto.getDimensions());
    product.setMetadata(dto.getMetadata());

    Product updated = productRepository.save(product);
    publishOutboxEvent("UPDATE", updated);
    log.info("Updated product [GREEN]: id={}", updated.getId());
    return updated;
  }

  @Transactional
  public void delete(String id) {
    Product product = getById(id);
    productRepository.delete(product);
    publishOutboxEvent("DELETE", product);
    log.info("Deleted product [GREEN]: id={}", id);
  }

  private Category resolveCategory(String name) {
    if (name == null) throw new RuntimeException("Category name required");
    return categoryRepository.findByName(name)
        .orElseGet(() -> categoryRepository.save(Category.builder().name(name).build()));
  }

  private Brand resolveBrand(String name) {
    if (name == null) return null;
    return brandRepository.findByName(name)
        .orElseGet(() -> brandRepository.save(Brand.builder().name(name).build()));
  }

  private Product.AvailabilityStatus parseStatus(String status) {
    if (status == null) return Product.AvailabilityStatus.IN_STOCK;
    try {
      return Product.AvailabilityStatus.valueOf(status.toUpperCase().replace(" ", "_"));
    } catch (Exception e) {
      return Product.AvailabilityStatus.IN_STOCK;
    }
  }

  private void publishOutboxEvent(String eventType, Product product) {
    try {
      String payload = objectMapper.writeValueAsString(product);
      OutboxEvent event = OutboxEvent.builder()
          .aggregateId(product.getId())
          .eventType(eventType)
          .payload(payload)
          .origin("GREEN")
          .processed(false)
          .build();
      outboxRepository.save(event);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize product for outbox", e);
      throw new RuntimeException("Outbox serialization failed", e);
    }
  }
  @Transactional
  public Product createFromReplication(ProductDto dto) {
    Category category = resolveCategory(dto.getCategoryName());
    Brand brand = resolveBrand(dto.getBrandName());

    Product product = Product.builder()
        .id(dto.getId())  // Nhận UUID từ Sync Bridge (đã có trong mapping)
        .title(dto.getTitle())
        .priceAmount(dto.getPriceAmount())
        .currency(dto.getCurrency() != null ? dto.getCurrency() : "USD")
        .discountPercentage(dto.getDiscountPercentage())
        .rating(dto.getRating())
        .stockQuantity(dto.getStockQuantity())
        .category(category)
        .brand(brand)
        .sku(dto.getSku())
        .availabilityStatus(parseStatus(dto.getAvailabilityStatus()))
        .dimensions(dto.getDimensions())
        .metadata(dto.getMetadata())
        .build();

    Product saved = productRepository.save(product);
    publishOutboxEventWithOrigin("CREATE", saved, "BLUE");
    log.info("Replicated CREATE [GREEN from BLUE]: id={}", saved.getId());
    return saved;
  }

  @Transactional
  public Product updateFromReplication(String id, ProductDto dto) {
    Product product = getById(id);
    if (dto.getTitle() != null) product.setTitle(dto.getTitle());
    if (dto.getPriceAmount() != null) product.setPriceAmount(dto.getPriceAmount());
    if (dto.getCurrency() != null) product.setCurrency(dto.getCurrency());
    if (dto.getDiscountPercentage() != null) product.setDiscountPercentage(dto.getDiscountPercentage());
    if (dto.getRating() != null) product.setRating(dto.getRating());
    if (dto.getStockQuantity() != null) product.setStockQuantity(dto.getStockQuantity());
    if (dto.getCategoryName() != null) product.setCategory(resolveCategory(dto.getCategoryName()));
    if (dto.getBrandName() != null) product.setBrand(resolveBrand(dto.getBrandName()));
    if (dto.getSku() != null) product.setSku(dto.getSku());
    if (dto.getAvailabilityStatus() != null) product.setAvailabilityStatus(parseStatus(dto.getAvailabilityStatus()));
    if (dto.getDimensions() != null) product.setDimensions(dto.getDimensions());
    if (dto.getMetadata() != null) product.setMetadata(dto.getMetadata());

    Product updated = productRepository.save(product);
    publishOutboxEventWithOrigin("UPDATE", updated, "BLUE");
    log.info("Replicated UPDATE [GREEN from BLUE]: id={}", updated.getId());
    return updated;
  }

  @Transactional
  public void deleteFromReplication(String id) {
    Product product = getById(id);
    productRepository.delete(product);
    publishOutboxEventWithOrigin("DELETE", product, "BLUE");
    log.info("Replicated DELETE [GREEN from BLUE]: id={}", id);
  }

  private void publishOutboxEventWithOrigin(String eventType, Product product, String origin) {
    try {
      String payload = objectMapper.writeValueAsString(product);
      OutboxEvent event = OutboxEvent.builder()
          .aggregateId(product.getId())
          .eventType(eventType)
          .payload(payload)
          .origin(origin)
          .processed(false)
          .build();
      outboxRepository.save(event);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize for outbox", e);
      throw new RuntimeException("Outbox serialization failed", e);
    }
  }
}