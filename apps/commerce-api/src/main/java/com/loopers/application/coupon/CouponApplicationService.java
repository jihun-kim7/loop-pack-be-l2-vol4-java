package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * 쿠폰 유스케이스 Application Service (스타일 2).
 *
 * <p>대고객(발급 접수/결과 조회/내 쿠폰 목록)과 어드민(템플릿 CRUD/발급내역)을 모두 담당한다.
 * 쿠폰 사용(주문 시 확정)은 주문 트랜잭션의 일부라 {@link com.loopers.application.order.OrderTransactionService}가 처리한다.
 *
 * <p><strong>비동기 발급 (Round 7)</strong>: 발급은 접수(202+requestId)만 하고, 실제 발급은
 * Kafka 컨슈머({@link CouponIssueProcessor})가 수행한다 — 선착순 폭주를 큐가 완충하고,
 * key=couponId 파티션 직렬화로 도착 순서대로 처리된다. 결과는 requestId 로 폴링한다.
 *
 * <p>발급 시 템플릿의 혜택이 발급분으로 스냅샷되므로("발급은 그 시점의 약속"),
 * 발급 이후의 조회/사용은 템플릿을 재조회하지 않는다.
 */
@RequiredArgsConstructor
@Service
public class CouponApplicationService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ===== 대고객 =====

    /**
     * 쿠폰 발급 요청 접수 — 요청을 저장하고 Kafka 발행(outbox)만 한다. 실제 발급은 컨슈머가 수행.
     *
     * <p>만료/중복은 접수 시점에 빠른 실패로 걸러 불필요한 큐 유입을 줄인다(UX 게이트).
     * 접수를 통과해도 최종 판정(수량/중복 재검증)은 컨슈머가 한다 — 접수 검사는 스냅샷일 뿐이다.
     *
     * <p>요청 행 INSERT 와 outbox 기록({@code OutboxEventListener}, BEFORE_COMMIT)이 같은
     * 트랜잭션이므로, 202 를 받은 요청은 반드시 컨슈머에 도달한다(At Least Once).
     */
    @Transactional
    public CouponIssueRequestInfo requestIssue(Long userId, Long couponTemplateId) {
        CouponModel template = couponRepository.findById(couponTemplateId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        if (template.isExpired(now)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰은 발급받을 수 없습니다.");
        }
        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponTemplateId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.");
        }

        CouponIssueRequest request = couponIssueRequestRepository.save(
            CouponIssueRequest.accept(userId, couponTemplateId));
        eventPublisher.publishEvent(
            new CouponIssueRequestedEvent(request.getRequestId(), userId, couponTemplateId));
        return CouponIssueRequestInfo.from(request, null);
    }

    /** 발급 요청 결과 폴링 — 본인 요청만 조회 가능(타 유저 요청은 존재 자체를 노출하지 않음). */
    @Transactional(readOnly = true)
    public CouponIssueRequestInfo getIssueRequest(Long userId, String requestId) {
        CouponIssueRequest request = couponIssueRequestRepository.findByRequestId(requestId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "발급 요청을 찾을 수 없습니다."));
        if (!request.isOwnedBy(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "발급 요청을 찾을 수 없습니다.");
        }

        UserCouponInfo issued = null;
        if (request.getUserCouponId() != null) {
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
            issued = userCouponRepository.findById(request.getUserCouponId())
                .map(uc -> UserCouponInfo.from(uc, now))
                .orElse(null);
        }
        return CouponIssueRequestInfo.from(request, issued);
    }

    /**
     * 내 쿠폰 목록 (AVAILABLE/USED/EXPIRED).
     *
     * <p>혜택이 발급분에 스냅샷되어 있어 템플릿 조인/일괄 조회 없이 발급분만으로 완성된다.
     */
    @Transactional(readOnly = true)
    public List<UserCouponInfo> getMyCoupons(Long userId) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        return userCouponRepository.findByUserId(userId).stream()
            .map(uc -> UserCouponInfo.from(uc, now))
            .toList();
    }

    // ===== 어드민 =====

    @Transactional
    public CouponInfo createTemplate(String name, CouponType type, long value, Long minOrderAmount,
                                     ZonedDateTime expiredAt, Integer totalQuantity) {
        CouponModel coupon = new CouponModel(name, type, value, minOrderAmount, expiredAt, totalQuantity);
        return CouponInfo.from(couponRepository.save(coupon));
    }

    @Transactional(readOnly = true)
    public CouponInfo getTemplate(Long couponId) {
        return CouponInfo.from(findTemplateOrThrow(couponId));
    }

    @Transactional(readOnly = true)
    public List<CouponInfo> getTemplates(int page, int size) {
        return couponRepository.findAll(page, size).stream()
            .map(CouponInfo::from)
            .toList();
    }

    /**
     * 템플릿 수정 — 이미 발급된 쿠폰의 혜택(스냅샷)에는 영향을 주지 않으며, 이후 발급분에만 적용된다.
     */
    @Transactional
    public CouponInfo updateTemplate(Long couponId, String name, CouponType type, long value, Long minOrderAmount,
                                     ZonedDateTime expiredAt, Integer totalQuantity) {
        CouponModel coupon = findTemplateOrThrow(couponId);
        coupon.update(name, type, value, minOrderAmount, expiredAt, totalQuantity);
        return CouponInfo.from(couponRepository.save(coupon));
    }

    @Transactional
    public void deleteTemplate(Long couponId) {
        CouponModel coupon = findTemplateOrThrow(couponId);
        if (userCouponRepository.existsByCouponId(couponId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 발급된 쿠폰이 있어 삭제할 수 없습니다.");
        }
        couponRepository.delete(coupon);
    }

    @Transactional(readOnly = true)
    public List<UserCouponInfo> getIssues(Long couponId, int page, int size) {
        findTemplateOrThrow(couponId);   // 템플릿 존재 검증
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        return userCouponRepository.findByCouponId(couponId, page, size).stream()
            .map(uc -> UserCouponInfo.from(uc, now))
            .toList();
    }

    private CouponModel findTemplateOrThrow(Long couponId) {
        return couponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + couponId + "] 쿠폰을 찾을 수 없습니다."));
    }
}
