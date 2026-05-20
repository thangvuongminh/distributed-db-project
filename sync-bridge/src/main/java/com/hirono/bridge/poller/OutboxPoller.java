package com.hirono.bridge.poller;

import com.hirono.bridge.entity.blue.BlueOutboxEvent;
import com.hirono.bridge.entity.green.GreenOutboxEvent;
import com.hirono.bridge.repository.blue.BlueOutboxRepository;
import com.hirono.bridge.repository.green.GreenOutboxRepository;
import com.hirono.bridge.translator.SchemaTranslator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Định kỳ poll outbox_events từ cả Blue và Green DB.
 * Khi tìm thấy event chưa xử lý -> dịch schema -> log ra (chưa replicate, Phần 2 sẽ làm).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {

  private final BlueOutboxRepository blueRepo;
  private final GreenOutboxRepository greenRepo;
  private final SchemaTranslator translator;

  @Scheduled(fixedDelayString = "${bridge.poll-interval-ms:2000}")
  public void pollBlue() {
    List<BlueOutboxEvent> events = blueRepo.findTop50ByProcessedFalseOrderByCreatedAtAsc();
    if (events.isEmpty()) return;

    log.info("Polling Blue outbox: found {} events", events.size());
    for (BlueOutboxEvent event : events) {
      try {
        processBlueEvent(event);
      } catch (Exception e) {
        log.error("Failed to process Blue event id={}: {}", event.getId(), e.getMessage(), e);
      }
    }
  }

  @Scheduled(fixedDelayString = "${bridge.poll-interval-ms:2000}")
  public void pollGreen() {
    List<GreenOutboxEvent> events = greenRepo.findTop50ByProcessedFalseOrderByCreatedAtAsc();
    if (events.isEmpty()) return;

    log.info("Polling Green outbox: found {} events", events.size());
    for (GreenOutboxEvent event : events) {
      try {
        processGreenEvent(event);
      } catch (Exception e) {
        log.error("Failed to process Green event id={}: {}", event.getId(), e.getMessage(), e);
      }
    }
  }

  @Transactional(transactionManager = "blueTransactionManager")
  public void processBlueEvent(BlueOutboxEvent event) throws Exception {
    // Loop prevention: chỉ xử lý event origin=BLUE (event do app gốc tạo ra)
    if (!"BLUE".equals(event.getOrigin())) {
      log.debug("Skipping Blue event id={} (origin={} -> from replication, not user)",
          event.getId(), event.getOrigin());
      event.setProcessed(true);
      blueRepo.save(event);
      return;
    }

    log.info("Processing Blue event: id={}, type={}, aggregateId={}",
        event.getId(), event.getEventType(), event.getAggregateId());

    // Dịch payload V1 -> V2
    String v2Payload = translator.translateV1toV2(event.getPayload());
    log.info("Translated V1->V2 payload: {}", v2Payload);

    // TODO: Phần 2 - gọi Replicator để ghi vào Green DB
    // Hiện tại chỉ mark là processed
    event.setProcessed(true);
    blueRepo.save(event);
    log.info("Marked Blue event id={} as processed", event.getId());
  }

  @Transactional(transactionManager = "greenTransactionManager")
  public void processGreenEvent(GreenOutboxEvent event) throws Exception {
    if (!"GREEN".equals(event.getOrigin())) {
      log.debug("Skipping Green event id={} (origin={} -> from replication, not user)",
          event.getId(), event.getOrigin());
      event.setProcessed(true);
      greenRepo.save(event);
      return;
    }

    log.info("Processing Green event: id={}, type={}, aggregateId={}",
        event.getId(), event.getEventType(), event.getAggregateId());

    // Dịch payload V2 -> V1
    String v1Payload = translator.translateV2toV1(event.getPayload());
    log.info("Translated V2->V1 payload: {}", v1Payload);

    // TODO: Phần 2 - gọi Replicator
    event.setProcessed(true);
    greenRepo.save(event);
    log.info("Marked Green event id={} as processed", event.getId());
  }
}