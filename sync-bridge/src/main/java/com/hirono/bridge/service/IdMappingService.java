package com.hirono.bridge.service;

import com.hirono.bridge.entity.blue.BlueIdMapping;
import com.hirono.bridge.entity.green.GreenIdMapping;
import com.hirono.bridge.repository.blue.BlueIdMappingRepository;
import com.hirono.bridge.repository.green.GreenIdMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Quản lý mapping giữa Blue.id (BIGINT) và Green.id (UUID).
 * Lưu cùng mapping trong cả 2 DB để tránh phụ thuộc 1 chiều.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdMappingService {

  private final BlueIdMappingRepository blueRepo;
  private final GreenIdMappingRepository greenRepo;

  /**
   * Khi event đến từ Blue (có id BIGINT), tìm hoặc tạo UUID tương ứng cho Green.
   */
  @Transactional(transactionManager = "blueTransactionManager")
  public String getOrCreateUuidForBlueId(Long blueId) {
    Optional<BlueIdMapping> existing = blueRepo.findById(blueId);
    if (existing.isPresent()) {
      return existing.get().getGreenUuid();
    }
    String uuid = UUID.randomUUID().toString();
    BlueIdMapping mapping = BlueIdMapping.builder()
        .blueId(blueId)
        .greenUuid(uuid)
        .build();
    blueRepo.save(mapping);
    log.info("Created new mapping: blueId={} <-> greenUuid={}", blueId, uuid);
    return uuid;
  }

  /**
   * Khi event đến từ Green (có UUID), tìm Blue id tương ứng.
   * Nếu chưa có, return null (caller sẽ phải tạo mới ở Blue).
   */
  @Transactional(readOnly = true, transactionManager = "greenTransactionManager")
  public Optional<Long> findBlueIdForUuid(String greenUuid) {
    return greenRepo.findById(greenUuid).map(GreenIdMapping::getBlueId);
  }

  /**
   * Khi product mới được tạo ở Green (UUID), Sync Bridge sẽ replicate sang Blue
   * và nhận được blueId mới. Lưu mapping này.
   */
  @Transactional(transactionManager = "greenTransactionManager")
  public void saveMappingFromGreen(String greenUuid, Long blueId) {
    GreenIdMapping mapping = GreenIdMapping.builder()
        .greenUuid(greenUuid)
        .blueId(blueId)
        .build();
    greenRepo.save(mapping);
    log.info("Saved mapping (from Green): greenUuid={} <-> blueId={}", greenUuid, blueId);
  }
}