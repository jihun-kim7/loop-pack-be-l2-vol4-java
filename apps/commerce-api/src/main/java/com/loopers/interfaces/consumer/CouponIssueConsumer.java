package com.loopers.interfaces.consumer;

import com.loopers.application.coupon.CouponIssueProcessor;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.confg.kafka.message.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 선착순 쿠폰 발급 컨슈머 — commerce-api 가 자기 토픽을 스스로 소비한다(self-consumption).
 *
 * <p>쿠폰 발급은 commerce-api 의 도메인(coupons/user_coupons)이므로 다른 서비스가 아닌
 * 자신이 소비한다 — 여기서 Kafka 는 서비스 간 전파가 아니라 <strong>폭주 완충 큐</strong>다.
 * key=couponId 라 같은 쿠폰의 요청은 한 파티션에서 도착 순서대로 처리된다(선착순).
 *
 * <p>manual ack — 배치 전체 처리 후에만 오프셋 커밋. 확정 실패(만료/중복/매진)는 프로세서가
 * FAILED 로 기록하고 정상 리턴하므로 재전달 루프가 없고, 인프라 오류만 예외로 전파되어
 * ack 없이 재전달된다(요청 행의 terminal 상태가 멱등 가드).
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class CouponIssueConsumer {

    private final CouponIssueProcessor couponIssueProcessor;

    @KafkaListener(
        topics = "${commerce-events.topics.coupon-issue}",
        groupId = "commerce-api-coupon-issue",
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void onCouponIssueRequested(List<EventEnvelope> messages, Acknowledgment acknowledgment) {
        for (EventEnvelope envelope : messages) {
            Object requestId = envelope.payload() == null ? null : envelope.payload().get("requestId");
            if (requestId == null) {
                log.warn("[CouponIssue] requestId 없는 메시지 — 건너뜀. eventId={}", envelope.eventId());
                continue;
            }
            couponIssueProcessor.process(requestId.toString());
        }
        acknowledgment.acknowledge();
    }
}
