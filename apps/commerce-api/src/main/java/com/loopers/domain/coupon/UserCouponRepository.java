package com.loopers.domain.coupon;

import java.util.List;
import java.util.Optional;

/** 발급된 사용자 쿠폰 저장소. */
public interface UserCouponRepository {
    UserCouponModel save(UserCouponModel userCoupon);

    /** 즉시 flush 하여 발급 시 중복(UK) 위반을 호출 경계 안에서 감지하기 위해 사용. */
    UserCouponModel saveAndFlush(UserCouponModel userCoupon);

    Optional<UserCouponModel> findById(Long id);
    boolean existsByUserIdAndCouponId(Long userId, Long couponId);
    boolean existsByCouponId(Long couponId);
    List<UserCouponModel> findByUserId(Long userId);
    List<UserCouponModel> findByCouponId(Long couponId, int page, int size);
}
