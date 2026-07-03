package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 선착순 쿠폰 발급 동시성 통합 테스트 (Round 7 Step 3).
 *
 * <p>실제 파이프라인에서 같은 쿠폰의 요청은 파티션 키(couponId)로 직렬화되지만,
 * 발급 처리기는 <strong>어떤 동시성에서도</strong> 초과 발급이 없어야 한다
 * (파티션 리밸런싱/운영 실수 등으로 병렬 소비가 일어나는 최악 상황 가정).
 * 조건부 원자 UPDATE({@code issued_count < total_quantity})가 그 보장의 근거다.
 */
@SpringBootTest
class CouponIssueConcurrencyIntegrationTest {

    @Autowired private CouponIssueProcessor couponIssueProcessor;
    @Autowired private CouponRepository couponRepository;
    @Autowired private CouponIssueRequestRepository couponIssueRequestRepository;
    @Autowired private UserCouponRepository userCouponRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("수량 10개 쿠폰에 30명이 동시에 발급을 요청해도, 정확히 10명만 발급된다 (초과 발급 없음).")
    @Test
    void issuesExactlyTotalQuantity_whenProcessedConcurrently() throws InterruptedException {
        // arrange — 수량 10 쿠폰 + 서로 다른 유저의 발급 요청 30건
        int totalQuantity = 10;
        int requestCount = 30;
        CouponModel coupon = couponRepository.save(new CouponModel(
            "선착순 1만원 할인", CouponType.FIXED, 10_000, null,
            ZonedDateTime.now().plusDays(1), totalQuantity));

        List<String> requestIds = new ArrayList<>();
        for (int i = 0; i < requestCount; i++) {
            CouponIssueRequest request = couponIssueRequestRepository.save(
                CouponIssueRequest.accept((long) (i + 1), coupon.getId()));
            requestIds.add(request.getRequestId());
        }

        // act — 30건을 동시에 처리 (실제로는 파티션 직렬화되지만 최악의 병렬 소비 가정)
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(requestCount);
        for (String requestId : requestIds) {
            executor.submit(() -> {
                try {
                    couponIssueProcessor.process(requestId);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // assert — 발급 수량이 정확히 total_quantity 와 일치 (초과 발급 없음)
        long issuedRequests = requestIds.stream()
            .map(id -> couponIssueRequestRepository.findByRequestId(id).orElseThrow())
            .filter(r -> r.getStatus() == CouponIssueRequest.Status.ISSUED)
            .count();
        long failedRequests = requestIds.stream()
            .map(id -> couponIssueRequestRepository.findByRequestId(id).orElseThrow())
            .filter(r -> r.getStatus() == CouponIssueRequest.Status.FAILED)
            .count();

        assertThat(issuedRequests).isEqualTo(totalQuantity);
        assertThat(failedRequests).isEqualTo(requestCount - totalQuantity);

        CouponModel updated = couponRepository.findById(coupon.getId()).orElseThrow();
        assertThat(updated.getIssuedCount()).isEqualTo(totalQuantity);
        assertThat(userCouponRepository.findByCouponId(coupon.getId(), 0, 100)).hasSize(totalQuantity);
    }

    @DisplayName("같은 요청이 재전달(중복 처리)되어도 발급은 한 번만 반영된다 (멱등).")
    @Test
    void processesOnlyOnce_whenSameRequestRedelivered() {
        // arrange — 수량 5 쿠폰, 요청 1건
        CouponModel coupon = couponRepository.save(new CouponModel(
            "선착순 쿠폰", CouponType.FIXED, 1_000, null, ZonedDateTime.now().plusDays(1), 5));
        CouponIssueRequest request = couponIssueRequestRepository.save(
            CouponIssueRequest.accept(1L, coupon.getId()));

        // act — 같은 requestId 를 3번 처리 (At Least Once 재전달 시뮬레이션)
        couponIssueProcessor.process(request.getRequestId());
        couponIssueProcessor.process(request.getRequestId());
        couponIssueProcessor.process(request.getRequestId());

        // assert — 수량 차감과 발급 모두 1회만
        assertThat(couponRepository.findById(coupon.getId()).orElseThrow().getIssuedCount()).isEqualTo(1L);
        assertThat(userCouponRepository.findByCouponId(coupon.getId(), 0, 100)).hasSize(1);
        assertThat(couponIssueRequestRepository.findByRequestId(request.getRequestId()).orElseThrow().getStatus())
            .isEqualTo(CouponIssueRequest.Status.ISSUED);
    }

    @DisplayName("같은 유저의 요청 두 건이 순차 처리되면, 두 번째는 중복으로 실패한다.")
    @Test
    void failsSecondRequest_whenSameUserRequestsTwice() {
        // arrange
        CouponModel coupon = couponRepository.save(new CouponModel(
            "선착순 쿠폰", CouponType.FIXED, 1_000, null, ZonedDateTime.now().plusDays(1), 5));
        CouponIssueRequest first = couponIssueRequestRepository.save(CouponIssueRequest.accept(1L, coupon.getId()));
        CouponIssueRequest second = couponIssueRequestRepository.save(CouponIssueRequest.accept(1L, coupon.getId()));

        // act — 파티션 직렬화와 동일하게 순차 처리
        couponIssueProcessor.process(first.getRequestId());
        couponIssueProcessor.process(second.getRequestId());

        // assert — 1건만 발급, 2번째는 FAILED(중복), 수량도 1만 차감
        assertThat(couponIssueRequestRepository.findByRequestId(first.getRequestId()).orElseThrow().getStatus())
            .isEqualTo(CouponIssueRequest.Status.ISSUED);
        CouponIssueRequest failed = couponIssueRequestRepository.findByRequestId(second.getRequestId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(CouponIssueRequest.Status.FAILED);
        assertThat(failed.getFailureReason()).contains("이미 발급");
        assertThat(couponRepository.findById(coupon.getId()).orElseThrow().getIssuedCount()).isEqualTo(1L);
    }
}
