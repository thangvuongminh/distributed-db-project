package com.hirono.bridge.translator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hirono.bridge.service.IdMappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Translator để dịch payload giữa V1 (Blue) và V2 (Green).
 * Đây là trái tim của Schema Mapping (Özsu Ch.4).
 *
 * V1 fields:                    V2 fields:
 *   id (BIGINT)         <-->     id (UUID)
 *   title                <-->    title (rename giữ nguyên)
 *   price                <-->    priceAmount + currency
 *   stock                <-->    stockQuantity
 *   category (string)    <-->    category.name (FK)
 *   brand (string)       <-->    brand.name (FK)
 *   availabilityStatus   <-->    availabilityStatus (string -> ENUM)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SchemaTranslator {

  private final IdMappingService idMappingService;
  private final ObjectMapper objectMapper = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  /**
   * Dịch payload từ V1 (Blue) sang V2 (Green).
   */
  public String translateV1toV2(String v1Payload) throws Exception {
    JsonNode v1 = objectMapper.readTree(v1Payload);
    ObjectNode v2 = objectMapper.createObjectNode();

    // 1. ID: BIGINT -> UUID (lấy từ mapping table)
    Long blueId = v1.get("id").asLong();
    String greenUuid = idMappingService.getOrCreateUuidForBlueId(blueId);
    v2.put("id", greenUuid);

    // 2. Title: giữ nguyên
    v2.put("title", textOrNull(v1, "title"));

    // 3. Price: tách thành priceAmount + currency
    if (v1.hasNonNull("price")) {
      v2.put("priceAmount", v1.get("price").decimalValue());
      v2.put("currency", "USD");  // Mặc định USD
    }

    // 4. Discount, rating
    if (v1.hasNonNull("discountPercentage")) {
      v2.put("discountPercentage", v1.get("discountPercentage").decimalValue());
    }
    if (v1.hasNonNull("rating")) {
      v2.put("rating", v1.get("rating").decimalValue());
    }

    // 5. Stock: rename
    if (v1.hasNonNull("stock")) {
      v2.put("stockQuantity", v1.get("stock").asInt());
    }

    // 6. Category: string -> categoryName (Green sẽ resolve thành FK)
    v2.put("categoryName", textOrNull(v1, "category"));

    // 7. Brand: string -> brandName
    v2.put("brandName", textOrNull(v1, "brand"));

    // 8. SKU
    v2.put("sku", textOrNull(v1, "sku"));

    // 9. Availability: "In Stock" -> "IN_STOCK"
    String availability = textOrNull(v1, "availabilityStatus");
    if (availability != null) {
      v2.put("availabilityStatus", normalizeStatusV1toV2(availability));
    }

    log.debug("Translated V1->V2: blueId={} -> uuid={}", blueId, greenUuid);
    return objectMapper.writeValueAsString(v2);
  }

  /**
   * Dịch payload từ V2 (Green) sang V1 (Blue).
   */
  public String translateV2toV1(String v2Payload) throws Exception {
    JsonNode v2 = objectMapper.readTree(v2Payload);
    ObjectNode v1 = objectMapper.createObjectNode();

    // 1. ID: UUID -> BIGINT (nếu đã có mapping)
    String greenUuid = v2.get("id").asText();
    idMappingService.findBlueIdForUuid(greenUuid).ifPresent(blueId -> v1.put("id", blueId));
    // Nếu chưa có mapping, v1 sẽ không có "id" -> Blue sẽ AUTO_INCREMENT tạo mới

    // 2. Title
    v1.put("title", textOrNull(v2, "title"));

    // 3. Price: gộp priceAmount + currency -> price
    // (Hiện tại chỉ lấy priceAmount, currency bị mất trong V1)
    if (v2.hasNonNull("priceAmount")) {
      v1.put("price", v2.get("priceAmount").decimalValue());
    }

    // 4. Discount, rating
    if (v2.hasNonNull("discountPercentage")) {
      v1.put("discountPercentage", v2.get("discountPercentage").decimalValue());
    }
    if (v2.hasNonNull("rating")) {
      v1.put("rating", v2.get("rating").decimalValue());
    }

    // 5. Stock: rename ngược
    if (v2.hasNonNull("stockQuantity")) {
      v1.put("stock", v2.get("stockQuantity").asInt());
    }

    // 6. Category: object {id, name} -> string
    if (v2.has("category") && v2.get("category").has("name")) {
      v1.put("category", v2.get("category").get("name").asText());
    }

    // 7. Brand: object {id, name} -> string
    if (v2.has("brand") && v2.get("brand").has("name")) {
      v1.put("brand", v2.get("brand").get("name").asText());
    }

    // 8. SKU
    v1.put("sku", textOrNull(v2, "sku"));

    // 9. Availability: "IN_STOCK" -> "In Stock"
    String availability = textOrNull(v2, "availabilityStatus");
    if (availability != null) {
      v1.put("availabilityStatus", normalizeStatusV2toV1(availability));
    }

    log.debug("Translated V2->V1: uuid={}", greenUuid);
    return objectMapper.writeValueAsString(v1);
  }

  private String textOrNull(JsonNode node, String field) {
    return node.hasNonNull(field) ? node.get(field).asText() : null;
  }

  private String normalizeStatusV1toV2(String v1Status) {
    // "In Stock" -> "IN_STOCK"
    return v1Status.toUpperCase().replace(" ", "_");
  }

  private String normalizeStatusV2toV1(String v2Status) {
    // "IN_STOCK" -> "In Stock"
    return switch (v2Status) {
      case "IN_STOCK" -> "In Stock";
      case "LOW_STOCK" -> "Low Stock";
      case "OUT_OF_STOCK" -> "Out of Stock";
      default -> v2Status;
    };
  }
}