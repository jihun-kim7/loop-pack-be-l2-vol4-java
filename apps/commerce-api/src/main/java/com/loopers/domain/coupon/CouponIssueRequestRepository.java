package com.loopers.domain.coupon;

import java.util.Optional;

/** 선착순 쿠폰 발급 요청 저장소. */
public interface CouponIssueRequestRepository {

    CouponIssueRequest save(CouponIssueRequest request);

    Optional<CouponIssueRequest> findByRequestId(String requestId);
}
