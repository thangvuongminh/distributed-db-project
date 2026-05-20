package com.hirono.bridge.replicator;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Gọi REST API của Blue/Green service để replicate data.
 * Sử dụng endpoint /_replicate (KHÔNG phải /api/products).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class Replicator {

  @Value("${bridge.blue-service-url:http://localhost:8081}")
  private String blueServiceUrl;

  @Value("${bridge.green-service-url:http://localhost:8082}")
  private String greenServiceUrl;

  private final RestTemplate restTemplate = new RestTemplate();
  private final ObjectMapper objectMapper = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  /**
   * Replicate event từ Blue sang Green.
   * @param eventType CREATE | UPDATE | DELETE
   * @param v2Payload JSON đã dịch sang V2
   * @return true nếu thành công, false nếu fail
   */
  public boolean replicateToGreen(String eventType, String v2Payload) {
    String url = greenServiceUrl + "/api/products/_replicate";
    return doReplicate(eventType, v2Payload, url, "GREEN");
  }

  /**
   * Replicate event từ Green sang Blue.
   */
  public boolean replicateToBlue(String eventType, String v1Payload) {
    String url = blueServiceUrl + "/api/products/_replicate";
    return doReplicate(eventType, v1Payload, url, "BLUE");
  }

  private boolean doReplicate(String eventType, String payload, String baseUrl, String targetCluster) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<String> entity = new HttpEntity<>(payload, headers);

    try {
      ResponseEntity<String> response;
      switch (eventType) {
        case "CREATE" -> response = restTemplate.exchange(
            baseUrl, HttpMethod.POST, entity, String.class);
        case "UPDATE" -> {
          String id = extractIdForUpdate(payload);
          response = restTemplate.exchange(
              baseUrl + "/" + id, HttpMethod.PUT, entity, String.class);
        }
        case "DELETE" -> {
          String id = extractIdForUpdate(payload);
          response = restTemplate.exchange(
              baseUrl + "/" + id, HttpMethod.DELETE, entity, String.class);
        }
        default -> {
          log.warn("Unknown event type: {}", eventType);
          return false;
        }
      }

      log.info("Replication SUCCESS to {} ({}): status={}",
          targetCluster, eventType, response.getStatusCode());
      return true;

    } catch (RestClientException e) {
      log.error("Replication FAILED to {} ({}): {}", targetCluster, eventType, e.getMessage());
      return false;
    }
  }

  private String extractIdForUpdate(String payload) throws RuntimeException {
    try {
      JsonNode node = objectMapper.readTree(payload);
      return node.get("id").asText();
    } catch (Exception e) {
      throw new RuntimeException("Cannot extract id from payload: " + payload, e);
    }
  }
}