package com.hirono.bridge.poller;

import com.hirono.bridge.entity.blue.BlueOutboxEvent;
import com.hirono.bridge.entity.green.GreenOutboxEvent;
import com.hirono.bridge.replicator.Replicator;
import com.hirono.bridge.repository.blue.BlueOutboxRepository;
import com.hirono.bridge.repository.green.GreenOutboxRepository;
import com.hirono.bridge.translator.SchemaTranslator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {

  private final BlueOutboxRepository blueRepo;
  private final GreenOutboxRepository greenRepo;
  private final SchemaTranslator translator;
  private final Replicator replicator;

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
    // LOOP PREVENTION
    if (!"BLUE".equals(event.getOrigin())) {
      log.debug("Skip Blue event id={} (origin={}, not from user)", event.getId(), event.getOrigin());
      event.setProcessed(true);
      blueRepo.save(event);
      return;
    }

    log.info("Processing Blue event: id={}, type={}, aggregateId={}",
        event.getId(), event.getEventType(), event.getAggregateId());

    // 1. Translate V1 -> V2
    String v2Payload = translator.translateV1toV2(event.getPayload());
    log.info("Translated V1->V2: {}", v2Payload);

    // 2. Replicate sang Green
    boolean success = replicator.replicateToGreen(event.getEventType(), v2Payload);

    // 3. Đánh dấu processed
    if (success) {
      event.setProcessed(true);
      blueRepo.save(event);
      log.info("✓ Blue event id={} replicated to Green", event.getId());
    } else {
      log.warn("✗ Blue event id={} NOT replicated, will retry next poll", event.getId());
      // KHÔNG mark processed -> sẽ poll lại lần sau
    }
  }

  @Transactional(transactionManager = "greenTransactionManager")
  public void processGreenEvent(GreenOutboxEvent event) throws Exception {
    if (!"GREEN".equals(event.getOrigin())) {
      log.debug("Skip Green event id={} (origin={}, not from user)", event.getId(), event.getOrigin());
      event.setProcessed(true);
      greenRepo.save(event);
      return;
    }

    log.info("Processing Green event: id={}, type={}, aggregateId={}",
        event.getId(), event.getEventType(), event.getAggregateId());

    String v1Payload = translator.translateV2toV1(event.getPayload());
    log.info("Translated V2->V1: {}", v1Payload);

    boolean success = replicator.replicateToBlue(event.getEventType(), v1Payload);

    if (success) {
      event.setProcessed(true);
      greenRepo.save(event);
      log.info("✓ Green event id={} replicated to Blue", event.getId());
    } else {
      log.warn("✗ Green event id={} NOT replicated, will retry next poll", event.getId());
    }
  }
}