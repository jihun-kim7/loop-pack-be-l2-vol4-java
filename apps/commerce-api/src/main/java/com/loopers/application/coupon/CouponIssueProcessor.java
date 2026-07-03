package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * 선착순 쿠폰 발급 처리 — Kafka 컨슈머가 요청 1건마다 호출하는 단일 트랜잭션.
 *
 * <h2>처리 규칙</h2>
 * <ol>
 *   <li><strong>멱등 가드</strong> — 요청이 이미 terminal(ISSUED/FAILED)이면 건너뛴다.
 *       재전달된 메시지가 중복 발급으로 이어지지 않는다.</li>
 *   <li><strong>확정 실패는 FAILED 로 기록하고 정상 리턴</strong> — 만료/중복/수량 소진은
 *       재시도해도 결과가 같은(결정적) 실패라, 예외로 던지면 재전달 무한 루프에 빠진다.</li>
 *   <li><strong>인프라 오류만 예외 전파</strong> — DB 장애 등은 트랜잭션 롤백 + ack 미수행으로
 *       재전달을 유도한다. 수량 증가·발급·요청 상태가 한 트랜잭션이라 부분 반영이 없다.</li>
 * </ol>
 *
 * <h2>동시성</h2>
 * <p>수량 차감은 조건부 원자 UPDATE({@code issued_count < total_quantity})라 어떤 동시성에서도
 * 초과 발급이 불가능하다. 같은 쿠폰의 요청은 파티션 키(couponId)로 직렬화되어 도착 순서대로
 * 처리되므로(선착순), 중복 발급 검사(exists)도 경합 없이 신뢰할 수 있다 —
 * 만에 하나 우회 경로가 생겨도 {@code (user_id, coupon_id)} UK 가 최후 방어선이며,
 * UK 위반 시 트랜잭션 전체(수량 증가 포함)가 롤백되어 정합성이 유지된다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class CouponIssueProcessor {

    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    @Transactional
    public void process(String requestId) {
        Optional<CouponIssueRequest> requestOpt = couponIssueRequestRepository.findByRequestId(requestId);
        if (requestOpt.isEmpty()) {
            // 접수 행과 outbox 가 같은 트랜잭션이라 정상 흐름에선 불가능 — 데이터 이상 신호만 남긴다
            log.warn("[CouponIssue] 요청을 찾을 수 없음 — 건너뜀. requestId={}", requestId);
            return;
        }
        CouponIssueRequest request = requestOpt.get();
        if (request.isTerminal()) {
            return;   // 이미 처리됨 — 재전달 멱등 스킵
        }

        Optional<CouponModel> templateOpt = couponRepository.findById(request.getCouponId());
        if (templateOpt.isEmpty()) {
            request.markFailed("쿠폰이 존재하지 않습니다.");
            return;
        }
        CouponModel template = templateOpt.get();

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        if (template.isExpired(now)) {
            request.markFailed("만료된 쿠폰입니다.");
            return;
        }
        if (userCouponRepository.existsByUserIdAndCouponId(request.getUserId(), request.getCouponId())) {
            request.markFailed("이미 발급받은 쿠폰입니다.");
            return;
        }

        // 선착순 수량 차감 — 조건부 원자 UPDATE. 0행 = 매진.
        int updated = couponRepository.tryIncreaseIssuedCount(request.getCouponId());
        if (updated == 0) {
            request.markFailed("쿠폰이 모두 소진되었습니다.");
            return;
        }

        UserCouponModel userCoupon = userCouponRepository.saveAndFlush(UserCouponModel.issue(request.getUserId(), template));
        request.markIssued(userCoupon.getId());
        log.info("[CouponIssue] 발급 완료. requestId={}, userId={}, couponId={}, userCouponId={}",
            requestId, request.getUserId(), request.getCouponId(), userCoupon.getId());
    }
}
