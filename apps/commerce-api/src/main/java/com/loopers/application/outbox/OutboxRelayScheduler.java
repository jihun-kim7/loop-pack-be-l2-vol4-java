package com.loopers.application.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Outbox 릴레이 — PENDING 이벤트를 폴링해 Kafka 로 발행하고 PUBLISHED 로 마킹한다.
 *
 * <h2>At Least Once</h2>
 * <p>발행 성공 후 마킹 전에 죽으면 재기동 시 같은 행을 다시 발행한다 — 유실은 없지만 중복은
 * 가능하다. 중복은 소비측(streamer)의 {@code event_handled} 멱등 처리가 흡수한다.
 *
 * <h2>순서 보장</h2>
 * <p>id 오름차순(기록 순서)으로 <strong>순차 발행</strong>하고, 실패 시 그 자리에서 중단해 다음 틱에
 * 재시도한다 — 실패 건을 건너뛰고 후속 건을 먼저 보내면 같은 파티션 키의 순서가 깨지기 때문.
 * {@code send().get()} 으로 브로커 ack(acks=all)를 확인한 뒤에만 마킹한다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class OutboxRelayScheduler {

    private static final int BATCH_SIZE = 100;
    private static final long SEND_TIMEOUT_SECONDS = 10L;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 1_000)
    public void relay() {
        List<OutboxEvent> pending = outboxEventRepository.findPending(BATCH_SIZE);
        for (OutboxEvent event : pending) {
            try {
                // payload 는 EventEnvelope 직렬화 전문 — JsonNode 로 되읽어 보내야 JsonSerializer 가
                // 문자열 재인용(이중 인코딩) 없이 원문 JSON 그대로 전송한다.
                kafkaTemplate.send(event.getTopic(), event.getPartitionKey(), objectMapper.readTree(event.getPayload()))
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                event.markPublished();
                outboxEventRepository.save(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[OutboxRelay] 발행 대기 중단 — 다음 틱 재시도. eventId={}", event.getEventId());
                return;
            } catch (Exception e) {
                // 순서 보장을 위해 여기서 중단 — 실패 건보다 뒤의 이벤트를 먼저 보내지 않는다.
                log.warn("[OutboxRelay] 발행 실패 — 다음 틱 재시도. eventId={}, topic={}",
                    event.getEventId(), event.getTopic(), e);
                return;
            }
        }
    }
}
